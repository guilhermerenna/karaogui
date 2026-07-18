package com.karaogui.backend.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "judge_assignment")
public class JudgeAssignment {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "judge_player_id", nullable = false)
    private UUID judgePlayerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JudgeSource source;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected JudgeAssignment() {}

    public JudgeAssignment(UUID id, UUID gameId, Long performanceId, UUID judgePlayerId, JudgeSource source) {
        this.id = id;
        this.gameId = gameId;
        this.performanceId = performanceId;
        this.judgePlayerId = judgePlayerId;
        this.source = source;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public Long getPerformanceId() { return performanceId; }
    public UUID getJudgePlayerId() { return judgePlayerId; }
    public JudgeSource getSource() { return source; }
    public Instant getSubmittedAt() { return submittedAt; }

    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
