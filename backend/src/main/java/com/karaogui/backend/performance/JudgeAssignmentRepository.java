package com.karaogui.backend.performance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JudgeAssignmentRepository extends JpaRepository<JudgeAssignment, UUID> {

    List<JudgeAssignment> findByPerformanceId(Long performanceId);

    Optional<JudgeAssignment> findByPerformanceIdAndJudgePlayerId(Long performanceId, UUID judgePlayerId);
}
