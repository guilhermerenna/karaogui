package com.karaogui.backend.auth;

import com.karaogui.backend.error.UnauthorizedException;
import com.karaogui.backend.player.GameDisplayTokenRepository;
import com.karaogui.backend.player.PlayerSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    static final String IDENTITY_ATTR = "playerIdentity";

    private final PlayerSessionRepository sessionRepo;
    private final GameDisplayTokenRepository displayTokenRepo;

    public TokenAuthFilter(PlayerSessionRepository sessionRepo,
            GameDisplayTokenRepository displayTokenRepo) {
        this.sessionRepo = sessionRepo;
        this.displayTokenRepo = displayTokenRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String rawToken = header.substring(7);
            String hash = sha256Hex(rawToken);

            // Try player session first
            sessionRepo.findByTokenHash(hash).ifPresentOrElse(
                    session -> request.setAttribute(IDENTITY_ATTR,
                            new PlayerIdentity(session.getPlayerId(), session.getGameId(), false)),
                    () ->
                    // Try display token
                    displayTokenRepo.findByTokenHash(hash).ifPresent(
                            dt -> request.setAttribute(IDENTITY_ATTR,
                                    new PlayerIdentity(null, dt.getGameId(), true))));
        }
        chain.doFilter(request, response);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
