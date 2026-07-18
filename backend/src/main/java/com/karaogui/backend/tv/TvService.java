package com.karaogui.backend.tv;

import com.karaogui.backend.auth.TokenAuthFilter;
import com.karaogui.backend.game.GameRepository;
import com.karaogui.backend.game.JoinCodeGenerator;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TvService {

    private static final int MAX_ATTEMPTS = 10;

    private final PendingTvSessionRepository pendingRepo;
    private final GameRepository gameRepo;
    private final JoinCodeGenerator joinCodeGenerator;

    public TvService(PendingTvSessionRepository pendingRepo, GameRepository gameRepo,
            JoinCodeGenerator joinCodeGenerator) {
        this.pendingRepo = pendingRepo;
        this.gameRepo = gameRepo;
        this.joinCodeGenerator = joinCodeGenerator;
    }

    @Transactional
    public TvRegisterResponse register() {
        String joinCode = generateUniqueCode();
        String rawToken = java.util.UUID.randomUUID().toString();
        String tokenHash = TokenAuthFilter.sha256Hex(rawToken);
        pendingRepo.save(new PendingTvSession(joinCode, tokenHash, Instant.now()));
        return new TvRegisterResponse(joinCode, JoinCodeGenerator.toDisplayFormat(joinCode), rawToken);
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
