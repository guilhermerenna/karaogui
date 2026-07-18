package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RatingId implements Serializable {

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    protected RatingId() {}

    public RatingId(Long performanceId, UUID playerId) {
        this.performanceId = performanceId;
        this.playerId = playerId;
    }

    public Long getPerformanceId() { return performanceId; }
    public UUID getPlayerId() { return playerId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RatingId r)) return false;
        return Objects.equals(performanceId, r.performanceId) && Objects.equals(playerId, r.playerId);
    }

    @Override
    public int hashCode() { return Objects.hash(performanceId, playerId); }
}
