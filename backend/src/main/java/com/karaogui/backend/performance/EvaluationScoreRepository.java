package com.karaogui.backend.performance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationScoreRepository extends JpaRepository<EvaluationScore, UUID> {

    List<EvaluationScore> findByEvaluationId(UUID evaluationId);

    @Modifying
    @Query("DELETE FROM EvaluationScore es WHERE es.evaluationId = :evaluationId")
    void deleteByEvaluationId(UUID evaluationId);
}
