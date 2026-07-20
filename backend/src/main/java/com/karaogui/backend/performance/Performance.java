package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "performance")
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerformanceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerformanceState state;

    @Column(name = "queue_position", nullable = false)
    private int queuePosition;

    @Column(name = "game_local_number", nullable = false)
    private int gameLocalNumber;

    @Column(name = "author_player_id")
    private UUID authorPlayerId;

    @Column(name = "youtube_url")
    private String youtubeUrl;

    @Column(name = "confirm_deadline_at")
    private Instant confirmDeadlineAt;

    @Column(name = "replacement_opens_at")
    private Instant replacementOpensAt;

    @Column(name = "announced_at")
    private Instant announcedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "judging_deadline_at")
    private Instant judgingDeadlineAt;

    @Version
    private long version;

    protected Performance() {}

    public Performance(UUID gameId, PerformanceType type, int queuePosition,
            int gameLocalNumber, UUID authorPlayerId, String youtubeUrl) {
        this.gameId = gameId;
        this.type = type;
        this.state = PerformanceState.QUEUED;
        this.queuePosition = queuePosition;
        this.gameLocalNumber = gameLocalNumber;
        this.authorPlayerId = authorPlayerId;
        this.youtubeUrl = youtubeUrl;
    }

    public Long getId() { return id; }
    public UUID getGameId() { return gameId; }
    public PerformanceType getType() { return type; }
    public PerformanceState getState() { return state; }
    public int getQueuePosition() { return queuePosition; }
    public int getGameLocalNumber() { return gameLocalNumber; }
    public UUID getAuthorPlayerId() { return authorPlayerId; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public Instant getConfirmDeadlineAt() { return confirmDeadlineAt; }
    public Instant getReplacementOpensAt() { return replacementOpensAt; }
    public Instant getAnnouncedAt() { return announcedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLockedAt() { return lockedAt; }

    public Long getDurationSeconds() { return durationSeconds; }
    public Instant getJudgingDeadlineAt() { return judgingDeadlineAt; }

    public void setState(PerformanceState state) { this.state = state; }
    public void setConfirmDeadlineAt(Instant confirmDeadlineAt) { this.confirmDeadlineAt = confirmDeadlineAt; }
    public void setReplacementOpensAt(Instant replacementOpensAt) { this.replacementOpensAt = replacementOpensAt; }
    public void setAnnouncedAt(Instant announcedAt) { this.announcedAt = announcedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setJudgingDeadlineAt(Instant judgingDeadlineAt) { this.judgingDeadlineAt = judgingDeadlineAt; }
}
