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

    private String topic(UUID gameId, String name) {
        return "/topic/games/" + gameId + "/" + name;
    }
}
