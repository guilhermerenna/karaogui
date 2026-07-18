package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rating")
public class Rating {

    @EmbeddedId
    private RatingId id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "total_score", nullable = false, columnDefinition = "numeric")
    private double totalScore;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Rating() {}

    public Rating(Long performanceId, UUID playerId, UUID gameId, Instant now) {
        this.id = new RatingId(performanceId, playerId);
        this.gameId = gameId;
        this.totalScore = 0;
        this.locked = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public RatingId getId() { return id; }
    public UUID getGameId() { return gameId; }
    public double getTotalScore() { return totalScore; }
    public boolean isLocked() { return locked; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
