package com.karaogui.backend.game;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.auth.ScopeGuard;
import com.karaogui.backend.auth.TokenAuthFilter;
import com.karaogui.backend.config.KaraoguiProperties;
import com.karaogui.backend.error.GameStateException;
import com.karaogui.backend.game.dto.CreateGameRequest;
import com.karaogui.backend.game.dto.CreateGameResponse;
import com.karaogui.backend.game.dto.GameSnapshotDto;
import com.karaogui.backend.game.dto.JoinGameRequest;
import com.karaogui.backend.game.dto.PlayerDto;
import com.karaogui.backend.game.dto.RankingPageDto;
import com.karaogui.backend.player.GameDisplayToken;
import com.karaogui.backend.player.GameDisplayTokenRepository;
import com.karaogui.backend.player.Player;
import com.karaogui.backend.player.PlayerRepository;
import com.karaogui.backend.player.PlayerSession;
import com.karaogui.backend.player.PlayerSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import com.karaogui.backend.tv.PendingTvSessionRepository;
import com.karaogui.backend.performance.PerformanceService;
import com.karaogui.backend.performance.PerformanceRepository;
import com.karaogui.backend.performance.PerformanceState;
import com.karaogui.backend.performance.dto.CurrentPerformanceDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameService {

    private static final int MAX_JOIN_CODE_ATTEMPTS = 10;
    private static final int MIN_PLAYERS_TO_START = 3;

    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final PlayerSessionRepository sessionRepo;
    private final GameDisplayTokenRepository displayTokenRepo;
    private final PendingTvSessionRepository pendingTvRepo;
    private final JoinCodeGenerator joinCodeGenerator;
    private final KaraoguiProperties props;
    private final ApplicationEventPublisher eventPublisher;
    private final PerformanceService performanceService;
    private final PerformanceRepository performanceRepo;

    public GameService(GameRepository gameRepo, PlayerRepository playerRepo,
            PlayerSessionRepository sessionRepo, GameDisplayTokenRepository displayTokenRepo,
            PendingTvSessionRepository pendingTvRepo,
            JoinCodeGenerator joinCodeGenerator, KaraoguiProperties props,
            ApplicationEventPublisher eventPublisher, PerformanceService performanceService,
            PerformanceRepository performanceRepo) {
        this.gameRepo = gameRepo;
        this.playerRepo = playerRepo;
        this.sessionRepo = sessionRepo;
        this.displayTokenRepo = displayTokenRepo;
        this.pendingTvRepo = pendingTvRepo;
        this.joinCodeGenerator = joinCodeGenerator;
        this.props = props;
        this.eventPublisher = eventPublisher;
        this.performanceService = performanceService;
        this.performanceRepo = performanceRepo;
    }

    @Transactional
    public CreateGameResponse createGame(CreateGameRequest req) {
        Instant now = Instant.now();
        UUID gameId = UUID.randomUUID();

        // Resolve join code and display token — either from a pending TV session or fresh
        String joinCode;
        String rawDisplayToken;
        String displayTokenHash;

        if (req.tvCode() != null && !req.tvCode().isBlank()) {
            String normalized = JoinCodeGenerator.normalize(req.tvCode());
            var pending = pendingTvRepo.findById(normalized)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("TV_SESSION_NOT_FOUND"));
            joinCode = normalized;
            displayTokenHash = pending.getDisplayTokenHash();
            // Reconstruct the raw token is not possible — we store the hash only.
            // Return a sentinel so the host knows the TV already has its token.
            rawDisplayToken = null;
            pendingTvRepo.delete(pending);
        } else {
            joinCode = generateUniqueJoinCode();
            rawDisplayToken = UUID.randomUUID().toString();
            displayTokenHash = TokenAuthFilter.sha256Hex(rawDisplayToken);
        }

        Game game = new Game(gameId, joinCode, GameState.CREATED, null, now);
        gameRepo.save(game);

        UUID playerId = UUID.randomUUID();
        Player host = new Player(playerId, gameId, req.host().displayName(), true, now);
        playerRepo.save(host);

        game.setHostPlayerId(playerId);
        gameRepo.save(game);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenAuthFilter.sha256Hex(rawToken);
        sessionRepo.save(new PlayerSession(UUID.randomUUID(), playerId, gameId, tokenHash, now));

        displayTokenRepo.save(new GameDisplayToken(UUID.randomUUID(), gameId, displayTokenHash, now));

        if (req.tvCode() != null && !req.tvCode().isBlank()) {
            eventPublisher.publishEvent(new GameDomainEvent.TvReady(this, displayTokenHash, gameId, joinCode));
        }

        return new CreateGameResponse(
                gameId,
                joinCode,
                JoinCodeGenerator.toDisplayFormat(joinCode),
                game.getState().name(),
                new CreateGameResponse.PlayerInfo(playerId, host.getDisplayName(), true),
                rawToken,
                rawDisplayToken);
    }

    @Transactional
    public CreateGameResponse joinGame(JoinGameRequest req) {
        String normalizedCode = JoinCodeGenerator.normalize(req.joinCode());
        Game game = gameRepo.findByJoinCode(normalizedCode)
                .orElseThrow(() -> new EntityNotFoundException("Game not found for code: " + normalizedCode));

        if (game.getState() == GameState.OVER) {
            throw new GameStateException("GAME_OVER", "This game has already ended.");
        }

        Instant now = Instant.now();
        UUID playerId = UUID.randomUUID();
        Player player = new Player(playerId, game.getId(), req.player().displayName(), false, now);
        playerRepo.save(player);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenAuthFilter.sha256Hex(rawToken);
        sessionRepo.save(new PlayerSession(UUID.randomUUID(), playerId, game.getId(), tokenHash, now));

        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);
        eventPublisher.publishEvent(
                new GameDomainEvent.PlayerJoined(this, game.getId(), playerId, player.getDisplayName(), seq));

        return new CreateGameResponse(
                game.getId(),
                game.getJoinCode(),
                JoinCodeGenerator.toDisplayFormat(game.getJoinCode()),
                game.getState().name(),
                new CreateGameResponse.PlayerInfo(playerId, player.getDisplayName(), false),
                rawToken,
                null);
    }

    @Transactional
    public GameSnapshotDto startGame(UUID gameId, PlayerIdentity identity) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));

        if (!game.getHostPlayerId().equals(identity.playerId())) {
            throw new com.karaogui.backend.auth.GameScopeException("FORBIDDEN",
                    "Only the host can start the game.");
        }
        if (game.getState() != GameState.CREATED) {
            throw new GameStateException("GAME_ALREADY_STARTED", "Game is not in CREATED state.");
        }

        List<Player> players = playerRepo.findAllByGameIdOrderByScoreDesc(gameId);
        // A performance needs at least 1 performer, 1 judge, and 1 audience member.
        if (players.size() < MIN_PLAYERS_TO_START) {
            throw new GameStateException("NOT_ENOUGH_PLAYERS",
                    "At least " + MIN_PLAYERS_TO_START + " players are needed to start the game.");
        }

        game.setState(GameState.ACTIVE);
        game.setStartedAt(Instant.now());

        long seqState = game.incrementAndGetSeq();
        long seqRanking = game.incrementAndGetSeq();
        gameRepo.save(game);
        eventPublisher.publishEvent(
                new GameDomainEvent.GameStarted(this, gameId, seqState, seqRanking, players.size()));

        return buildSnapshot(game);
    }

    @Transactional(readOnly = true)
    public GameSnapshotDto getSnapshot(UUID gameId, PlayerIdentity identity) {
        ScopeGuard.requireScope(identity, gameId);
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));
        return buildSnapshot(game);
    }

    @Transactional(readOnly = true)
    public GameSnapshotDto getSnapshotByCode(String joinCode, PlayerIdentity identity) {
        String normalized = JoinCodeGenerator.normalize(joinCode);
        Game game = gameRepo.findByJoinCode(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Game not found for code: " + joinCode));
        ScopeGuard.requireScope(identity, game.getId());
        return buildSnapshot(game);
    }


    public List<PlayerDto> listPlayers(UUID gameId, PlayerIdentity identity) {
        ScopeGuard.requireScope(identity, gameId);
        gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));
        return playerRepo.findAllByGameIdOrderByScoreDesc(gameId).stream()
                .map(this::toPlayerDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerDto getMe(UUID gameId, PlayerIdentity identity) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        return playerRepo.findByIdAndGameId(identity.playerId(), gameId)
                .map(this::toPlayerDto)
                .orElseThrow(() -> new EntityNotFoundException("Player not found."));
    }

    @Transactional(readOnly = true)
    public RankingPageDto getRankingPage(UUID gameId, PlayerIdentity identity, int page) {
        ScopeGuard.requireScope(identity, gameId);
        gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));
        List<Player> all = playerRepo.findAllByGameIdOrderByScoreDesc(gameId);
        return buildRankingPage(all, page);
    }

    @Transactional
    public void endGame(UUID gameId, PlayerIdentity identity) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));
        if (game.getState() != GameState.ACTIVE) {
            throw new GameStateException("GAME_NOT_ACTIVE", "Game must be ACTIVE to end.");
        }
        boolean hasQueued = !performanceRepo
                .findByGameIdAndStateOrderByQueuePositionAsc(gameId, PerformanceState.QUEUED)
                .isEmpty();
        if (hasQueued) {
            throw new GameStateException("QUEUE_NOT_EMPTY", "There are queued performances. Play them first.");
        }
        game.setState(GameState.OVER);
        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);
        eventPublisher.publishEvent(new GameDomainEvent.GameEnded(this, gameId, seq));
    }

    private GameSnapshotDto buildSnapshot(Game game) {
        List<Player> players = playerRepo.findAllByGameIdOrderByScoreDesc(game.getId());
        List<PlayerDto> playerDtos = players.stream().map(this::toPlayerDto).toList();
        RankingPageDto ranking = buildRankingPage(players, 0);
        CurrentPerformanceDto currentPerformance = performanceService.findCurrentPerformance(game.getId())
                .orElse(null);
        boolean queueNonEmpty = !performanceRepo
                .findByGameIdAndStateOrderByQueuePositionAsc(game.getId(), PerformanceState.QUEUED)
                .isEmpty();
        return new GameSnapshotDto(
                game.getId(),
                game.getJoinCode(),
                JoinCodeGenerator.toDisplayFormat(game.getJoinCode()),
                game.getState().name(),
                playerDtos,
                currentPerformance,
                ranking,
                queueNonEmpty);
    }

    private RankingPageDto buildRankingPage(List<Player> all, int page) {
        int pageSize = props.scoreboard().maxDisplayedPlayer();
        int total = all.size();
        int fromIndex = page * pageSize;
        List<RankingPageDto.Entry> entries = new ArrayList<>();
        if (fromIndex < total) {
            List<Player> pageSlice = all.subList(fromIndex, Math.min(fromIndex + pageSize, total));
            for (int i = 0; i < pageSlice.size(); i++) {
                Player p = pageSlice.get(i);
                entries.add(new RankingPageDto.Entry(fromIndex + i + 1, p.getId(),
                        p.getDisplayName(), p.getScore()));
            }
        }
        return new RankingPageDto(page, pageSize, total, entries);
    }

    private PlayerDto toPlayerDto(Player p) {
        boolean onBreak = p.getOnBreakUntil() != null && p.getOnBreakUntil().isAfter(Instant.now());
        return new PlayerDto(p.getId(), p.getDisplayName(), p.getPictureUrl(),
                p.getScore(), p.isHost(), onBreak);
    }

    private String generateUniqueJoinCode() {
        for (int attempt = 0; attempt < MAX_JOIN_CODE_ATTEMPTS; attempt++) {
            String code = joinCodeGenerator.generate();
            if (gameRepo.findByJoinCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique join code after "
                + MAX_JOIN_CODE_ATTEMPTS + " attempts.");
    }
}
