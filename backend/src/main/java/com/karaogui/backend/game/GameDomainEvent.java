package com.karaogui.backend.game;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEvent;

public abstract sealed class GameDomainEvent extends ApplicationEvent
        permits GameDomainEvent.PlayerJoined, GameDomainEvent.GameStarted,
                GameDomainEvent.GameEnded, GameDomainEvent.TvReady,
                GameDomainEvent.PerformanceAnnounced, GameDomainEvent.SlotStateChanged,
                GameDomainEvent.PerformanceStarted, GameDomainEvent.PerformanceLocked,
                GameDomainEvent.PerformanceSkipped, GameDomainEvent.RankingUpdated,
                GameDomainEvent.CommentPosted, GameDomainEvent.CommentLiked {

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

    public static final class PerformanceAnnounced extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final Long performanceId;
        private final String type;
        private final List<GameEvent.SlotInfo> slots;
        private final List<String> judgeNames;
        private final String youtubeUrl;
        private final java.time.Instant confirmDeadlineAt;

        public PerformanceAnnounced(Object source, UUID gameId, long seq, Long performanceId,
                String type, List<GameEvent.SlotInfo> slots, List<String> judgeNames,
                String youtubeUrl, java.time.Instant confirmDeadlineAt) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.performanceId = performanceId;
            this.type = type;
            this.slots = slots;
            this.judgeNames = judgeNames;
            this.youtubeUrl = youtubeUrl;
            this.confirmDeadlineAt = confirmDeadlineAt;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public Long performanceId() { return performanceId; }
        public String type() { return type; }
        public List<GameEvent.SlotInfo> slots() { return slots; }
        public List<String> judgeNames() { return judgeNames; }
        public String youtubeUrl() { return youtubeUrl; }
        public java.time.Instant confirmDeadlineAt() { return confirmDeadlineAt; }
    }

    public static final class SlotStateChanged extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final Long performanceId;
        private final UUID slotId;
        private final String slotState;
        private final String currentPlayerName;

        public SlotStateChanged(Object source, UUID gameId, long seq, Long performanceId,
                UUID slotId, String slotState, String currentPlayerName) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.performanceId = performanceId;
            this.slotId = slotId;
            this.slotState = slotState;
            this.currentPlayerName = currentPlayerName;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public Long performanceId() { return performanceId; }
        public UUID slotId() { return slotId; }
        public String slotState() { return slotState; }
        public String currentPlayerName() { return currentPlayerName; }
    }

    public static final class PerformanceStarted extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final Long performanceId;

        public PerformanceStarted(Object source, UUID gameId, long seq, Long performanceId) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.performanceId = performanceId;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public Long performanceId() { return performanceId; }
    }

    public static final class PerformanceLocked extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final Long performanceId;
        private final List<GameEvent.ScoreResult> scores;

        public PerformanceLocked(Object source, UUID gameId, long seq, Long performanceId,
                List<GameEvent.ScoreResult> scores) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.performanceId = performanceId;
            this.scores = scores;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public Long performanceId() { return performanceId; }
        public List<GameEvent.ScoreResult> scores() { return scores; }
    }

    public static final class PerformanceSkipped extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final Long performanceId;

        public PerformanceSkipped(Object source, UUID gameId, long seq, Long performanceId) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.performanceId = performanceId;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public Long performanceId() { return performanceId; }
    }

    public static final class RankingUpdated extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final List<com.karaogui.backend.game.dto.RankingPageDto.Entry> entries;
        private final int totalPlayers;

        public RankingUpdated(Object source, UUID gameId, long seq,
                List<com.karaogui.backend.game.dto.RankingPageDto.Entry> entries, int totalPlayers) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.entries = entries;
            this.totalPlayers = totalPlayers;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public List<com.karaogui.backend.game.dto.RankingPageDto.Entry> entries() { return entries; }
        public int totalPlayers() { return totalPlayers; }
    }

    public static final class TvReady extends GameDomainEvent {
        private final String displayTokenHash;
        private final UUID gameId;
        private final String joinCode;

        public TvReady(Object source, String displayTokenHash, UUID gameId, String joinCode) {
            super(source);
            this.displayTokenHash = displayTokenHash;
            this.gameId = gameId;
            this.joinCode = joinCode;
        }

        public String displayTokenHash() { return displayTokenHash; }
        public UUID gameId() { return gameId; }
        public String joinCode() { return joinCode; }
    }

    public static final class GameEnded extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;

        public GameEnded(Object source, UUID gameId, long seq) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
    }

    public static final class CommentPosted extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final UUID commentId;
        private final UUID authorPlayerId;
        private final String authorName;
        private final String body;
        private final java.time.Instant createdAt;

        public CommentPosted(Object source, UUID gameId, long seq, UUID commentId,
                UUID authorPlayerId, String authorName, String body, java.time.Instant createdAt) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.commentId = commentId;
            this.authorPlayerId = authorPlayerId;
            this.authorName = authorName;
            this.body = body;
            this.createdAt = createdAt;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public UUID commentId() { return commentId; }
        public UUID authorPlayerId() { return authorPlayerId; }
        public String authorName() { return authorName; }
        public String body() { return body; }
        public java.time.Instant createdAt() { return createdAt; }
    }

    public static final class CommentLiked extends GameDomainEvent {
        private final UUID gameId;
        private final long seq;
        private final UUID commentId;
        private final int likeCount;

        public CommentLiked(Object source, UUID gameId, long seq, UUID commentId, int likeCount) {
            super(source);
            this.gameId = gameId;
            this.seq = seq;
            this.commentId = commentId;
            this.likeCount = likeCount;
        }

        public UUID gameId() { return gameId; }
        public long seq() { return seq; }
        public UUID commentId() { return commentId; }
        public int likeCount() { return likeCount; }
    }
}
