package com.karaogui.backend.game;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

public abstract sealed class GameDomainEvent extends ApplicationEvent
        permits GameDomainEvent.PlayerJoined, GameDomainEvent.GameStarted {

    protected GameDomainEvent(Object source) {
        super(source);
    }

    public static final class PlayerJoined extends GameDomainEvent {
        private final UUID gameId;
        private final UUID playerId;
        private final String displayName;
        private final long seq;

        public PlayerJoined(Object source, UUID gameId, UUID playerId, String displayName, long seq) {
            super(source);
            this.gameId = gameId;
            this.playerId = playerId;
            this.displayName = displayName;
            this.seq = seq;
        }

        public UUID gameId() { return gameId; }
        public UUID playerId() { return playerId; }
        public String displayName() { return displayName; }
        public long seq() { return seq; }
    }

    public static final class GameStarted extends GameDomainEvent {
        private final UUID gameId;
        private final long seqState;
        private final long seqRanking;
        private final int totalPlayers;

        public GameStarted(Object source, UUID gameId, long seqState, long seqRanking, int totalPlayers) {
            super(source);
            this.gameId = gameId;
            this.seqState = seqState;
            this.seqRanking = seqRanking;
            this.totalPlayers = totalPlayers;
        }

        public UUID gameId() { return gameId; }
        public long seqState() { return seqState; }
        public long seqRanking() { return seqRanking; }
        public int totalPlayers() { return totalPlayers; }
    }
}
