package com.karaogui.backend.tv;

import com.karaogui.backend.auth.TokenAuthFilter;
import com.karaogui.backend.error.GameStateException;
import com.karaogui.backend.game.GameRepository;
import com.karaogui.backend.game.JoinCodeGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TvService {

    private static final int MAX_ATTEMPTS = 10;
    public static final Duration PENDING_TTL = Duration.ofMinutes(30);
    public static final int MAX_PENDING_SESSIONS = 50;

    private final PendingTvSessionRepository pendingRepo;
    private final GameRepository gameRepo;
    private final JoinCodeGenerator joinCodeGenerator;
    private final Clock clock;

    public TvService(PendingTvSessionRepository pendingRepo, GameRepository gameRepo,
            JoinCodeGenerator joinCodeGenerator, Clock clock) {
        this.pendingRepo = pendingRepo;
        this.gameRepo = gameRepo;
        this.joinCodeGenerator = joinCodeGenerator;
        this.clock = clock;
    }

    @Transactional
    public TvRegisterResponse register() {
        Instant now = clock.instant();
        pendingRepo.deleteByCreatedAtBefore(now.minus(PENDING_TTL));
        if (pendingRepo.count() >= MAX_PENDING_SESSIONS) {
            throw new GameStateException("TOO_MANY_PENDING_TV_SESSIONS",
                    "Too many pending TV sessions. Please try again shortly.");
        }

        String joinCode = generateUniqueCode();
        String rawToken = java.util.UUID.randomUUID().toString();
        String tokenHash = TokenAuthFilter.sha256Hex(rawToken);
        pendingRepo.save(new PendingTvSession(joinCode, tokenHash, now));
        return new TvRegisterResponse(joinCode, JoinCodeGenerator.toDisplayFormat(joinCode), rawToken);
    }

    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void purgeExpiredSessions() {
        pendingRepo.deleteByCreatedAtBefore(clock.instant().minus(PENDING_TTL));
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = joinCodeGenerator.generate();
            if (gameRepo.findByJoinCode(code).isEmpty() && pendingRepo.findById(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique TV join code.");
    }
}
