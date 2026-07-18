package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "rating_score")
public class RatingScore {

    @EmbeddedId
    private RatingScoreId id;

    @Column(nullable = false, columnDefinition = "numeric")
    private double value;

    protected RatingScore() {}

    public RatingScore(Long performanceId, java.util.UUID playerId, String criterion, double value) {
        this.id = new RatingScoreId(performanceId, playerId, criterion);
        this.value = value;
    }

    public RatingScoreId getId() { return id; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}
