package com.karaogui.backend.performance;

import com.karaogui.backend.auth.GameScopeException;
import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.auth.ScopeGuard;
import com.karaogui.backend.config.KaraoguiProperties;
import com.karaogui.backend.error.GameStateException;
import com.karaogui.backend.game.Game;
import com.karaogui.backend.game.GameDomainEvent;
import com.karaogui.backend.game.GameEvent;
import com.karaogui.backend.game.GameRepository;
import com.karaogui.backend.game.GameState;
import com.karaogui.backend.game.JoinCodeGenerator;
import com.karaogui.backend.game.dto.RankingPageDto;
import com.karaogui.backend.performance.dto.CriterionScore;
import com.karaogui.backend.performance.dto.CurrentPerformanceDto;
import com.karaogui.backend.performance.dto.EvaluateRequest;
import com.karaogui.backend.performance.dto.JudgeAssignmentDto;
import com.karaogui.backend.performance.dto.PerformanceDetailDto;
import com.karaogui.backend.performance.dto.QueuePerformanceRequest;
import com.karaogui.backend.performance.dto.RateRequest;
import com.karaogui.backend.performance.dto.ScoreResultDto;
import com.karaogui.backend.performance.dto.SlotDto;
import com.karaogui.backend.player.Player;
import com.karaogui.backend.player.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceService {

    private static final List<String> KARAOKE_CRITERIA = List.of("PITCH", "ENERGY", "STAGE_PRESENCE");

    private final PerformanceRepository performanceRepo;
    private final PerformerSlotRepository slotRepo;
    private final JudgeAssignmentRepository judgeRepo;
    private final EvaluationRepository evalRepo;
    private final EvaluationScoreRepository evalScoreRepo;
    private final RatingRepository ratingRepo;
    private final RatingScoreRepository ratingScoreRepo;
    private final PlayerRepository playerRepo;
    private final GameRepository gameRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final KaraoguiProperties props;
    private final Clock clock;

    public PerformanceService(
            PerformanceRepository performanceRepo,
            PerformerSlotRepository slotRepo,
            JudgeAssignmentRepository judgeRepo,
            EvaluationRepository evalRepo,
            EvaluationScoreRepository evalScoreRepo,
            RatingRepository ratingRepo,
            RatingScoreRepository ratingScoreRepo,
            PlayerRepository playerRepo,
            GameRepository gameRepo,
            ApplicationEventPublisher eventPublisher,
            KaraoguiProperties props,
            Clock clock) {
        this.performanceRepo = performanceRepo;
        this.slotRepo = slotRepo;
        this.judgeRepo = judgeRepo;
        this.evalRepo = evalRepo;
        this.evalScoreRepo = evalScoreRepo;
        this.ratingRepo = ratingRepo;
        this.ratingScoreRepo = ratingScoreRepo;
        this.playerRepo = playerRepo;
        this.gameRepo = gameRepo;
        this.eventPublisher = eventPublisher;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public PerformanceDetailDto queuePerformance(UUID gameId, PlayerIdentity identity,
            QueuePerformanceRequest req) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Game game = requireGame(gameId);
        if (game.getState() != GameState.ACTIVE) {
            throw new GameStateException("GAME_NOT_ACTIVE", "Game must be ACTIVE to queue a performance.");
        }
        PerformanceType type = PerformanceType.valueOf(req.type());

        List<Player> allPlayers = playerRepo.findAllByGameIdOrderByScoreDesc(gameId);
        int nextQueuePos = performanceRepo.findByGameIdOrderByQueuePositionAsc(gameId).size();
        int gameLocalNumber = nextQueuePos + 1;

        Performance performance = new Performance(
                gameId, type, nextQueuePos, gameLocalNumber,
                identity.playerId(), req.youtubeUrl());
        performanceRepo.save(performance);

        List<UUID> requestedIds = req.performerPlayerIds() != null ? req.performerPlayerIds() : List.of();
        List<UUID> performerIds = resolvePerformerSlots(requestedIds, allPlayers, identity.playerId());

        for (int i = 0; i < performerIds.size(); i++) {
            UUID pid = performerIds.get(i);
            SlotOrigin origin = i < requestedIds.size() && requestedIds.contains(pid)
                    ? SlotOrigin.PREDETERMINED : SlotOrigin.RANDOM;
            slotRepo.save(new PerformerSlot(UUID.randomUUID(), gameId, performance.getId(), i, origin, pid));
        }

        for (Player p : allPlayers) {
            if (!performerIds.contains(p.getId())) {
                judgeRepo.save(new JudgeAssignment(UUID.randomUUID(), gameId,
                        performance.getId(), p.getId(), JudgeSource.ASSIGNED));
            }
        }

        boolean hasActive = performanceRepo
                .findTopByGameIdAndStateInOrderByQueuePositionAsc(gameId,
                        List.of(PerformanceState.CONFIRMING, PerformanceState.RUNNING))
                .isPresent();
        if (!hasActive) {
            announceNext(game, performance);
        }

        return buildDetail(performance, identity.playerId());
    }

    @Transactional(readOnly = true)
    public PerformanceDetailDto getDetail(UUID gameId, Long performanceId, PlayerIdentity identity) {
        ScopeGuard.requireScope(identity, gameId);
        Performance perf = requirePerformance(performanceId, gameId);
        return buildDetail(perf, identity.isDisplayToken() ? null : identity.playerId());
    }

    @Transactional
    public void confirmSlot(UUID gameId, Long performanceId, PlayerIdentity identity) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Performance perf = requirePerformance(performanceId, gameId);
        if (perf.getState() != PerformanceState.CONFIRMING) {
            throw new GameStateException("WRONG_STATE", "Performance is not in CONFIRMING state.");
        }

        PerformerSlot slot = slotRepo.findByPerformanceIdAndCurrentPlayerId(performanceId, identity.playerId())
                .orElseThrow(() -> new GameStateException("NOT_A_PERFORMER", "You are not assigned to this performance."));
        if (slot.getState() != SlotState.PENDING) {
            throw new GameStateException("ALREADY_CONFIRMED", "Slot already confirmed.");
        }

        slot.setState(SlotState.CONFIRMED);
        slot.setConfirmedAt(clock.instant());
        slotRepo.save(slot);

        Game game = requireGame(gameId);
        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);

        Player player = requirePlayer(identity.playerId(), gameId);
        eventPublisher.publishEvent(new GameDomainEvent.SlotStateChanged(
                this, gameId, seq, performanceId, slot.getId(),
                SlotState.CONFIRMED.name(), player.getDisplayName()));

        checkAndStart(game, perf);
    }

    @Transactional
    public void volunteerSlot(UUID gameId, Long performanceId, PlayerIdentity identity) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Performance perf = requirePerformance(performanceId, gameId);
        if (perf.getState() != PerformanceState.CONFIRMING) {
            throw new GameStateException("WRONG_STATE", "Performance is not in CONFIRMING state.");
        }
        if (!clock.instant().isAfter(perf.getReplacementOpensAt())) {
            throw new GameStateException("REPLACEMENT_NOT_OPEN", "Volunteer window has not opened yet.");
        }

        List<PerformerSlot> slots = slotRepo.findByPerformanceIdOrderBySlotIndex(performanceId);
        PerformerSlot openSlot = slots.stream()
                .filter(s -> s.getState() == SlotState.PENDING)
                .findFirst()
                .orElseThrow(() -> new GameStateException("NO_OPEN_SLOT", "No open slots available."));

        openSlot.setCurrentPlayerId(identity.playerId());
        openSlot.setState(SlotState.REPLACED);
        openSlot.setConfirmedAt(clock.instant());
        slotRepo.save(openSlot);

        Game game = requireGame(gameId);
        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);

        Player player = requirePlayer(identity.playerId(), gameId);
        eventPublisher.publishEvent(new GameDomainEvent.SlotStateChanged(
                this, gameId, seq, performanceId, openSlot.getId(),
                SlotState.REPLACED.name(), player.getDisplayName()));

        checkAndStart(game, perf);
    }

    @Transactional
    public void submitEvaluation(UUID gameId, Long performanceId, PlayerIdentity identity,
            EvaluateRequest req) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Performance perf = requirePerformance(performanceId, gameId);
        if (perf.getState() != PerformanceState.RUNNING) {
            throw new GameStateException("WRONG_STATE", "Performance is not RUNNING.");
        }

        JudgeAssignment assignment = judgeRepo.findByPerformanceIdAndJudgePlayerId(
                performanceId, identity.playerId())
                .orElseThrow(() -> new GameScopeException("NOT_A_JUDGE", "You are not a judge for this performance."));

        Evaluation evaluation = evalRepo
                .findByPerformanceIdAndJudgePlayerId(performanceId, identity.playerId())
                .orElseGet(() -> new Evaluation(UUID.randomUUID(), gameId, performanceId, identity.playerId()));

        evaluation.setSubmittedAt(clock.instant());
        evalRepo.save(evaluation);

        evalScoreRepo.deleteByEvaluationId(evaluation.getId());

        List<EvaluationScore> scores = new ArrayList<>();
        if (req.baseline() != null) {
            for (CriterionScore cs : req.baseline()) {
                scores.add(new EvaluationScore(UUID.randomUUID(), evaluation.getId(), null, cs.criterion(), cs.score()));
            }
        }
        if (req.perPerformer() != null) {
            for (Map.Entry<UUID, List<CriterionScore>> entry : req.perPerformer().entrySet()) {
                for (CriterionScore cs : entry.getValue()) {
                    scores.add(new EvaluationScore(UUID.randomUUID(), evaluation.getId(),
                            entry.getKey(), cs.criterion(), cs.score()));
                }
            }
        }
        evalScoreRepo.saveAll(scores);

        assignment.setSubmittedAt(clock.instant());
        judgeRepo.save(assignment);

        boolean allSubmitted = judgeRepo.findByPerformanceId(performanceId).stream()
                .allMatch(a -> a.getSubmittedAt() != null);
        if (allSubmitted) {
            lockPerformance(gameId, perf);
        }
    }

    @Transactional
    public void submitRating(UUID gameId, Long performanceId, PlayerIdentity identity, RateRequest req) {
        ScopeGuard.requirePlayerScope(identity, gameId);
        Performance perf = requirePerformance(performanceId, gameId);
        if (perf.getState() == PerformanceState.LOCKED) {
            throw new GameStateException("PERFORMANCE_LOCKED", "Performance is already locked.");
        }
        if (perf.getState() != PerformanceState.RUNNING) {
            throw new GameStateException("WRONG_STATE", "Performance is not RUNNING.");
        }

        boolean isPerformer = slotRepo.findByPerformanceIdAndCurrentPlayerId(performanceId, identity.playerId())
                .isPresent();
        if (isPerformer) {
            throw new GameScopeException("PERFORMER_CANNOT_RATE", "Performers cannot rate their own performance.");
        }

        RatingId ratingId = new RatingId(performanceId, identity.playerId());
        Rating rating = ratingRepo.findById(ratingId)
                .orElseGet(() -> new Rating(performanceId, identity.playerId(), gameId, clock.instant()));
        rating.setTotalScore(req.overallScore());
        rating.setUpdatedAt(clock.instant());
        ratingRepo.save(rating);

        ratingScoreRepo.deleteByPerformanceIdAndPlayerId(performanceId, identity.playerId());
        ratingScoreRepo.save(new RatingScore(performanceId, identity.playerId(), "OVERALL", req.overallScore()));
    }

    @Transactional
    public void skipPerformance(Long performanceId) {
        Performance perf = performanceRepo.findById(performanceId)
                .orElseThrow(() -> new EntityNotFoundException("Performance not found: " + performanceId));
        if (perf.getState() != PerformanceState.CONFIRMING) {
            return;
        }
        perf.setState(PerformanceState.SKIPPED);
        performanceRepo.save(perf);

        Game game = requireGame(perf.getGameId());
        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);

        eventPublisher.publishEvent(
                new GameDomainEvent.PerformanceSkipped(this, perf.getGameId(), seq, performanceId));

        announceNextQueued(game);
    }

    public Optional<CurrentPerformanceDto> findCurrentPerformance(UUID gameId) {
        return performanceRepo.findTopByGameIdAndStateInOrderByQueuePositionAsc(
                        gameId, List.of(PerformanceState.CONFIRMING, PerformanceState.RUNNING,
                                PerformanceState.LOCKED))
                .map(p -> toCurrentDto(p, slotRepo.findByPerformanceIdOrderBySlotIndex(p.getId())));
    }

    private void announceNext(Game game, Performance perf) {
        Instant now = clock.instant();
        perf.setState(PerformanceState.CONFIRMING);
        perf.setAnnouncedAt(now);
        perf.setConfirmDeadlineAt(now.plus(props.timers().readyConfirmWindow()));
        perf.setReplacementOpensAt(now.plus(props.timers().replacementOpenAfter()));
        performanceRepo.save(perf);

        List<PerformerSlot> slots = slotRepo.findByPerformanceIdOrderBySlotIndex(perf.getId());
        Map<UUID, String> playerNames = buildPlayerNameMap(perf.getGameId());

        List<GameEvent.SlotInfo> slotInfos = slots.stream()
                .map(s -> new GameEvent.SlotInfo(s.getId(), s.getSlotIndex(), s.getCurrentPlayerId(),
                        playerNames.getOrDefault(s.getCurrentPlayerId(), "?"), s.getState().name()))
                .toList();

        List<JudgeAssignment> judges = judgeRepo.findByPerformanceId(perf.getId());
        List<String> judgeNames = judges.stream()
                .map(j -> playerNames.getOrDefault(j.getJudgePlayerId(), "?"))
                .toList();

        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);

        eventPublisher.publishEvent(new GameDomainEvent.PerformanceAnnounced(
                this, game.getId(), seq, perf.getId(), perf.getType().name(),
                slotInfos, judgeNames, perf.getYoutubeUrl(), perf.getConfirmDeadlineAt()));
    }

    private void announceNextQueued(Game game) {
        performanceRepo.findTopByGameIdAndStateInOrderByQueuePositionAsc(
                        game.getId(), List.of(PerformanceState.QUEUED))
                .ifPresent(next -> announceNext(game, next));
    }

    private void checkAndStart(Game game, Performance perf) {
        perf = performanceRepo.findById(perf.getId()).orElseThrow();
        List<PerformerSlot> slots = slotRepo.findByPerformanceIdOrderBySlotIndex(perf.getId());
        boolean allResolved = slots.stream()
                .allMatch(s -> s.getState() == SlotState.CONFIRMED || s.getState() == SlotState.REPLACED
                        || s.getState() == SlotState.VACATED);
        if (allResolved) {
            startPerformance(game, perf);
        }
    }

    private void startPerformance(Game game, Performance perf) {
        perf.setState(PerformanceState.RUNNING);
        perf.setStartedAt(clock.instant());
        performanceRepo.save(perf);

        long seq = game.incrementAndGetSeq();
        gameRepo.save(game);

        eventPublisher.publishEvent(
                new GameDomainEvent.PerformanceStarted(this, game.getId(), seq, perf.getId()));
    }

    private void lockPerformance(UUID gameId, Performance perf) {
        perf.setState(PerformanceState.LOCKED);
        perf.setLockedAt(clock.instant());
        performanceRepo.save(perf);

        List<ScoreResultDto> scoreResults = computeAndApplyScores(gameId, perf);

        Game game = requireGame(gameId);
        long seqLocked = game.incrementAndGetSeq();
        long seqRanking = game.incrementAndGetSeq();
        gameRepo.save(game);

        List<Player> allPlayers = playerRepo.findAllByGameIdOrderByScoreDesc(gameId);
        List<RankingPageDto.Entry> rankingEntries = new ArrayList<>();
        for (int i = 0; i < allPlayers.size(); i++) {
            Player p = allPlayers.get(i);
            rankingEntries.add(new RankingPageDto.Entry(i + 1, p.getId(), p.getDisplayName(), p.getScore()));
        }

        List<GameEvent.ScoreResult> eventScores = scoreResults.stream()
                .map(r -> new GameEvent.ScoreResult(r.playerId(), r.displayName(), r.points()))
                .toList();

        eventPublisher.publishEvent(new GameDomainEvent.PerformanceLocked(
                this, gameId, seqLocked, perf.getId(), eventScores));

        eventPublisher.publishEvent(new GameDomainEvent.RankingUpdated(
                this, gameId, seqRanking, rankingEntries, allPlayers.size()));

        announceNextQueued(game);
    }

    private List<ScoreResultDto> computeAndApplyScores(UUID gameId, Performance perf) {
        List<PerformerSlot> slots = slotRepo.findByPerformanceIdOrderBySlotIndex(perf.getId());
        List<PerformerSlot> activeSlots = slots.stream()
                .filter(s -> s.getState() == SlotState.CONFIRMED || s.getState() == SlotState.REPLACED)
                .toList();

        List<Evaluation> evaluations = evalRepo.findByPerformanceId(perf.getId());

        List<RatingScore> audienceRatings = ratingScoreRepo
                .findByIdPerformanceIdAndIdCriterion(perf.getId(), "OVERALL");
        double audienceAvg = audienceRatings.stream()
                .mapToDouble(RatingScore::getValue)
                .average()
                .orElse(Double.NaN);

        List<ScoreResultDto> results = new ArrayList<>();

        for (PerformerSlot slot : activeSlots) {
            UUID performerId = slot.getCurrentPlayerId();
            double judgeScore = computeJudgeScore(performerId, evaluations);
            double A = Double.isNaN(audienceAvg) ? judgeScore : audienceAvg;
            double judgeWeight = props.scoring().subjectiveJudgeWeight();
            double audienceWeight = props.scoring().subjectiveAudienceWeight();
            double raw = judgeWeight * judgeScore + audienceWeight * A;
            int points = (int) Math.round(raw * props.scoring().subjectivePointScale());

            Player player = playerRepo.findByIdAndGameId(performerId, gameId).orElse(null);
            if (player != null) {
                player.addScore(points);
                player.incrementPerformanceParticipationCount();
                playerRepo.save(player);
                results.add(new ScoreResultDto(performerId, player.getDisplayName(), points));
            }
        }

        return results;
    }

    private double computeJudgeScore(UUID performerId, List<Evaluation> evaluations) {
        if (evaluations.isEmpty()) return 5.0;
        double total = 0;
        int count = 0;
        for (Evaluation eval : evaluations) {
            List<EvaluationScore> scores = evalScoreRepo.findByEvaluationId(eval.getId());
            double criteriaTotal = 0;
            int criteriaCount = 0;
            for (String criterion : KARAOKE_CRITERIA) {
                double score = findScore(scores, performerId, criterion);
                criteriaTotal += score;
                criteriaCount++;
            }
            if (criteriaCount > 0) {
                total += criteriaTotal / criteriaCount;
                count++;
            }
        }
        return count > 0 ? total / count : 5.0;
    }

    private double findScore(List<EvaluationScore> scores, UUID performerId, String criterion) {
        return scores.stream()
                .filter(s -> criterion.equals(s.getCriterion())
                        && performerId.equals(s.getSubjectPlayerId()))
                .findFirst()
                .map(EvaluationScore::getValue)
                .orElseGet(() -> scores.stream()
                        .filter(s -> criterion.equals(s.getCriterion()) && s.getSubjectPlayerId() == null)
                        .findFirst()
                        .map(EvaluationScore::getValue)
                        .orElse(5.0));
    }

    private List<UUID> resolvePerformerSlots(List<UUID> requested, List<Player> allPlayers, UUID authorId) {
        List<UUID> result = new ArrayList<>(requested);
        List<UUID> eligible = allPlayers.stream()
                .map(Player::getId)
                .filter(id -> !result.contains(id))
                .filter(id -> !id.equals(authorId))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(eligible);
        int needed = Math.min(4, allPlayers.size() - 1) - result.size(); // -1 to exclude author
        for (int i = 0; i < needed && i < eligible.size(); i++) {
            result.add(eligible.get(i));
        }
        return result;
    }

    private PerformanceDetailDto buildDetail(Performance perf, UUID requestingPlayerId) {
        List<PerformerSlot> slots = slotRepo.findByPerformanceIdOrderBySlotIndex(perf.getId());
        List<JudgeAssignment> judges = judgeRepo.findByPerformanceId(perf.getId());
        Map<UUID, String> playerNames = buildPlayerNameMap(perf.getGameId());

        List<SlotDto> slotDtos = slots.stream()
                .map(s -> new SlotDto(s.getId(), s.getSlotIndex(), s.getCurrentPlayerId(),
                        playerNames.getOrDefault(s.getCurrentPlayerId(), "?"), s.getState().name()))
                .toList();

        List<JudgeAssignmentDto> judgeDtos = judges.stream()
                .map(j -> new JudgeAssignmentDto(j.getJudgePlayerId(),
                        playerNames.getOrDefault(j.getJudgePlayerId(), "?"),
                        j.getSubmittedAt() != null))
                .toList();

        boolean evalSubmitted = false;
        boolean ratingSubmitted = false;
        if (requestingPlayerId != null) {
            evalSubmitted = evalRepo.findByPerformanceIdAndJudgePlayerId(perf.getId(), requestingPlayerId)
                    .map(e -> e.getSubmittedAt() != null).orElse(false);
            ratingSubmitted = ratingRepo.findById(new RatingId(perf.getId(), requestingPlayerId)).isPresent();
        }

        return new PerformanceDetailDto(
                perf.getId(), perf.getType().name(), perf.getState().name(),
                perf.getYoutubeUrl(), perf.getConfirmDeadlineAt(), perf.getReplacementOpensAt(),
                slotDtos, judgeDtos, evalSubmitted, ratingSubmitted);
    }

    CurrentPerformanceDto toCurrentDto(Performance perf, List<PerformerSlot> slots) {
        Map<UUID, String> playerNames = buildPlayerNameMap(perf.getGameId());
        List<SlotDto> slotDtos = slots.stream()
                .map(s -> new SlotDto(s.getId(), s.getSlotIndex(), s.getCurrentPlayerId(),
                        playerNames.getOrDefault(s.getCurrentPlayerId(), "?"), s.getState().name()))
                .toList();
        return new CurrentPerformanceDto(
                perf.getId(), perf.getType().name(), perf.getState().name(),
                perf.getYoutubeUrl(), perf.getConfirmDeadlineAt(), perf.getReplacementOpensAt(),
                slotDtos);
    }

    private Map<UUID, String> buildPlayerNameMap(UUID gameId) {
        return playerRepo.findAllByGameIdOrderByScoreDesc(gameId).stream()
                .collect(Collectors.toMap(Player::getId, Player::getDisplayName));
    }

    private Game requireGame(UUID gameId) {
        return gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found: " + gameId));
    }

    private Player requirePlayer(UUID playerId, UUID gameId) {
        return playerRepo.findByIdAndGameId(playerId, gameId)
                .orElseThrow(() -> new EntityNotFoundException("Player not found: " + playerId));
    }

    private Performance requirePerformance(Long performanceId, UUID gameId) {
        Performance perf = performanceRepo.findById(performanceId)
                .orElseThrow(() -> new EntityNotFoundException("Performance not found: " + performanceId));
        if (!perf.getGameId().equals(gameId)) {
            throw new GameScopeException("FORBIDDEN", "Performance does not belong to this game.");
        }
        return perf;
    }
}
