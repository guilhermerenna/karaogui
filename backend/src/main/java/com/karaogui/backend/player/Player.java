package com.karaogui.backend.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player")
public class Player {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "picture_url")
    private String pictureUrl;

    @Column(nullable = false)
    private int score;

    @Column(name = "performance_participation_count", nullable = false)
    private int performanceParticipationCount;

    @Column(name = "on_break_until")
    private Instant onBreakUntil;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Version
    private long version;

    protected Player() {}

    public Player(UUID id, UUID gameId, String displayName, boolean isHost, Instant joinedAt) {
        this.id = id;
        this.gameId = gameId;
        this.displayName = displayName;
        this.isHost = isHost;
        this.joinedAt = joinedAt;
        this.score = 0;
        this.performanceParticipationCount = 0;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public String getDisplayName() { return displayName; }
    public String getPictureUrl() { return pictureUrl; }
    public int getScore() { return score; }
    public int getPerformanceParticipationCount() { return performanceParticipationCount; }
    public Instant getOnBreakUntil() { return onBreakUntil; }
    public boolean isHost() { return isHost; }
    public Instant getJoinedAt() { return joinedAt; }

    public void setOnBreakUntil(Instant onBreakUntil) { this.onBreakUntil = onBreakUntil; }
}
