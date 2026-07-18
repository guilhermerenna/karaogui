package com.karaogui.backend.game;

import com.karaogui.backend.game.dto.RankingPageDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GameEventPublisher {

    private final SimpMessagingTemplate messaging;

    public GameEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTvReady(GameDomainEvent.TvReady event) {
        messaging.convertAndSendToUser(
                event.displayTokenHash(),
                "/queue/tv-ready",
                java.util.Map.of(
                        "type", "TV_READY",
                        "gameId", event.gameId().toString(),
                        "joinCode", event.joinCode(),
                        "joinCodeDisplay", com.karaogui.backend.game.JoinCodeGenerator.toDisplayFormat(event.joinCode())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(GameDomainEvent.PlayerJoined event) {
        GameEvent gameEvent = new GameEvent(
                event.seq(),
                "PLAYER_JOINED",
                Instant.now(),
                new GameEvent.PlayerJoinedData(event.playerId(), event.displayName()));
        publishPlayers(event.gameId(), gameEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameStarted(GameDomainEvent.GameStarted event) {
        Instant now = Instant.now();
        publishState(event.gameId(), new GameEvent(
                event.seqState(),
                "GAME_STARTED",
                now,
                new GameEvent.GameStartedData(event.gameId())));

        publishRanking(event.gameId(), new GameEvent(
                event.seqRanking(),
                "RANKING_UPDATED",
                now,
                new GameEvent.RankingUpdatedData(List.of(), event.totalPlayers())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPerformanceAnnounced(GameDomainEvent.PerformanceAnnounced event) {
        publishPerformers(event.gameId(), new GameEvent(
                event.seq(),
                "PERFORMANCE_ANNOUNCED",
                Instant.now(),
                new GameEvent.PerformanceAnnouncedData(
                        event.performanceId(), event.type(), event.slots(),
                        event.judgeNames(), event.judgePlayerIds(), event.youtubeUrl(), event.confirmDeadlineAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotStateChanged(GameDomainEvent.SlotStateChanged event) {
        publishPerformers(event.gameId(), new GameEvent(
                event.seq(),
                "SLOT_STATE_CHANGED",
                Instant.now(),
                new GameEvent.SlotStateChangedData(
                        event.performanceId(), event.slotId(),
                        event.slotState(), event.currentPlayerName())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPerformanceStarted(GameDomainEvent.PerformanceStarted event) {
        publishPerformers(event.gameId(), new GameEvent(
                event.seq(),
                "PERFORMANCE_STARTED",
                Instant.now(),
                new GameEvent.PerformanceStartedData(event.performanceId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPerformanceLocked(GameDomainEvent.PerformanceLocked event) {
        publishPerformers(event.gameId(), new GameEvent(
                event.seq(),
                "PERFORMANCE_LOCKED",
                Instant.now(),
                new GameEvent.PerformanceLockedData(event.performanceId(), event.scores())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPerformanceSkipped(GameDomainEvent.PerformanceSkipped event) {
        publishPerformers(event.gameId(), new GameEvent(
                event.seq(),
                "PERFORMANCE_SKIPPED",
                Instant.now(),
                new GameEvent.PerformanceSkippedData(event.performanceId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRankingUpdated(GameDomainEvent.RankingUpdated event) {
        publishRanking(event.gameId(), new GameEvent(
                event.seq(),
                "RANKING_UPDATED",
                Instant.now(),
                new GameEvent.RankingUpdatedData(event.entries(), event.totalPlayers())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameEnded(GameDomainEvent.GameEnded event) {
        publishState(event.gameId(), new GameEvent(
                event.seq(),
                "GAME_ENDED",
                Instant.now(),
                new GameEvent.GameEndedData(event.gameId().toString())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentPosted(GameDomainEvent.CommentPosted event) {
        publishComments(event.gameId(), new GameEvent(
                event.seq(),
                "COMMENT_POSTED",
                Instant.now(),
                new GameEvent.CommentPostedData(event.commentId(), event.authorPlayerId(),
                        event.authorName(), event.body(), event.createdAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentLiked(GameDomainEvent.CommentLiked event) {
        publishComments(event.gameId(), new GameEvent(
                event.seq(),
                "COMMENT_LIKED",
                Instant.now(),
                new GameEvent.CommentLikedData(event.commentId(), event.likeCount())));
    }

    private void publishState(UUID gameId, GameEvent event) {
        messaging.convertAndSend(topic(gameId, "state"), event);
    }

    private void publishPlayers(UUID gameId, GameEvent event) {
        messaging.convertAndSend(topic(gameId, "players"), event);
    }

    private void publishRanking(UUID gameId, GameEvent event) {
        messaging.convertAndSend(topic(gameId, "ranking"), event);
    }

    private void publishPerformers(UUID gameId, GameEvent event) {
        messaging.convertAndSend(topic(gameId, "performers"), event);
    }

    private void publishComments(UUID gameId, GameEvent event) {
        messaging.convertAndSend(topic(gameId, "comments"), event);
    }

    private String topic(UUID gameId, String name) {
        return "/topic/games/" + gameId + "/" + name;
    }
}
