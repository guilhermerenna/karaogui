package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RatingScoreId implements Serializable {

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(nullable = false)
    private String criterion;

    protected RatingScoreId() {}

    public RatingScoreId(Long performanceId, UUID playerId, String criterion) {
        this.performanceId = performanceId;
        this.playerId = playerId;
        this.criterion = criterion;
    }

    public Long getPerformanceId() { return performanceId; }
    public UUID getPlayerId() { return playerId; }
    public String getCriterion() { return criterion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RatingScoreId r)) return false;
        return Objects.equals(performanceId, r.performanceId) && Objects.equals(playerId, r.playerId)
                && Objects.equals(criterion, r.criterion);
    }

    @Override
    public int hashCode() { return Objects.hash(performanceId, playerId, criterion); }
}
