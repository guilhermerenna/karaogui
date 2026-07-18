package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "evaluation_score")
public class EvaluationScore {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "subject_player_id")
    private UUID subjectPlayerId;

    @Column(nullable = false)
    private String criterion;

    @Column(nullable = false, columnDefinition = "numeric")
    private double value;

    protected EvaluationScore() {}

    public EvaluationScore(UUID id, UUID evaluationId, UUID subjectPlayerId, String criterion, double value) {
        this.id = id;
        this.evaluationId = evaluationId;
        this.subjectPlayerId = subjectPlayerId;
        this.criterion = criterion;
        this.value = value;
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getSubjectPlayerId() { return subjectPlayerId; }
    public String getCriterion() { return criterion; }
    public double getValue() { return value; }
}
