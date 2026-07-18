package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation")
public class Evaluation {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "judge_player_id", nullable = false)
    private UUID judgePlayerId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected Evaluation() {}

    public Evaluation(UUID id, UUID gameId, Long performanceId, UUID judgePlayerId) {
        this.id = id;
        this.gameId = gameId;
        this.performanceId = performanceId;
        this.judgePlayerId = judgePlayerId;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public Long getPerformanceId() { return performanceId; }
    public UUID getJudgePlayerId() { return judgePlayerId; }
    public Instant getSubmittedAt() { return submittedAt; }

    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
