package com.karaogui.backend.auth;

import com.karaogui.backend.player.GameDisplayTokenRepository;
import com.karaogui.backend.player.PlayerSessionRepository;
import com.karaogui.backend.tv.PendingTvSessionRepository;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final PlayerSessionRepository sessionRepo;
    private final GameDisplayTokenRepository displayTokenRepo;
    private final PendingTvSessionRepository pendingTvRepo;
    private final SimpUserRegistry userRegistry;
    private final Map<String, StompPrincipal> sessionPrincipals = new ConcurrentHashMap<>();

    public StompAuthInterceptor(PlayerSessionRepository sessionRepo,
            GameDisplayTokenRepository displayTokenRepo, PendingTvSessionRepository pendingTvRepo,
            @Lazy SimpUserRegistry userRegistry) {
        this.sessionRepo = sessionRepo;
        this.displayTokenRepo = displayTokenRepo;
        this.pendingTvRepo = pendingTvRepo;
        this.userRegistry = userRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String token = extractToken(accessor);
            if (token == null) throw new SecurityException("Missing Authorization header");

            String hash = TokenAuthFilter.sha256Hex(token);
            StompPrincipal principal = resolveToken(hash);
            if (principal == null) throw new SecurityException("Invalid token");

            String surfaceHeader = accessor.getFirstNativeHeader("surface");
            if (surfaceHeader != null && principal.displayOnly() && principal.gameId() != null) {
                principal = new StompPrincipal(null, principal.gameId(), "TV", true);
            }

            accessor.setUser(principal);
            var attrs = accessor.getSessionAttributes();
            if (attrs != null) attrs.put("principal", principal);
            String sessionId = accessor.getSessionId();
            if (sessionId != null) sessionPrincipals.put(sessionId, principal);
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            String sessionId = accessor.getSessionId();
            if (sessionId != null) sessionPrincipals.remove(sessionId);
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            StompPrincipal principal = resolveSessionPrincipal(accessor, message);
            if (principal == null) throw new SecurityException("Not authenticated");

            String destination = accessor.getDestination();
            if (destination == null) return message;

            // Pending TV: only /user/queue/tv-ready allowed
            if (principal.gameId() == null) {
                if (!destination.equals("/user/queue/tv-ready")) {
                    throw new SecurityException("Pending TV session may only subscribe to /user/queue/tv-ready");
                }
                return message;
            }

            UUID destinationGameId = extractGameId(destination);
            if (destinationGameId != null && !destinationGameId.equals(principal.gameId())) {
                throw new SecurityException("Cannot subscribe to a different game's topics");
            }

            if (principal.displayOnly() && destination.startsWith("/user/")) {
                throw new SecurityException("Display token cannot subscribe to private topics");
            }
        }

        return message;
    }

    private StompPrincipal resolveSessionPrincipal(StompHeaderAccessor accessor, Message<?> message) {
        Principal fromHeader = message.getHeaders().get(SimpMessageHeaderAccessor.USER_HEADER, Principal.class);
        if (fromHeader instanceof StompPrincipal sp) return sp;
        Principal fromAccessor = accessor.getUser();
        if (fromAccessor instanceof StompPrincipal sp) return sp;
        var attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("principal") instanceof StompPrincipal sp) return sp;
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            StompPrincipal fromMap = sessionPrincipals.get(sessionId);
            if (fromMap != null) return fromMap;
            for (var user : userRegistry.getUsers()) {
                SimpSession simpSession = user.getSession(sessionId);
                if (simpSession != null && user.getPrincipal() instanceof StompPrincipal sp) {
                    return sp;
                }
            }
        }
        return null;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private StompPrincipal resolveToken(String hash) {
        var session = sessionRepo.findByTokenHash(hash);
        if (session.isPresent()) {
            var s = session.get();
            return new StompPrincipal(s.getPlayerId(), s.getGameId(), "PHONE", false);
        }
        var displayToken = displayTokenRepo.findByTokenHash(hash);
        if (displayToken.isPresent()) {
            return new StompPrincipal(null, displayToken.get().getGameId(), "TV", true);
        }
        // Pending TV session — no game yet; use hash as principal name for user-destination routing
        var pending = pendingTvRepo.findByDisplayTokenHash(hash);
        if (pending.isPresent()) {
            return new StompPrincipal(null, null, "TV", true, hash);
        }
        return null;
    }

    private UUID extractGameId(String destination) {
        String prefix = "/topic/games/";
        if (!destination.startsWith(prefix)) return null;
        String rest = destination.substring(prefix.length());
        int slash = rest.indexOf('/');
        String idStr = slash >= 0 ? rest.substring(0, slash) : rest;
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
