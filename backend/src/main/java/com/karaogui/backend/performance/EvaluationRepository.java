package com.karaogui.backend.performance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    Optional<Evaluation> findByPerformanceIdAndJudgePlayerId(Long performanceId, UUID judgePlayerId);

    java.util.List<Evaluation> findByPerformanceId(Long performanceId);
}
