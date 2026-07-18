package com.karaogui.backend.performance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    List<Performance> findByGameIdOrderByQueuePositionAsc(UUID gameId);

    List<Performance> findByGameIdAndStateOrderByQueuePositionAsc(UUID gameId, PerformanceState state);

    List<Performance> findByStateAndConfirmDeadlineAtBefore(PerformanceState state, Instant deadline);

    Optional<Performance> findTopByGameIdAndStateInOrderByQueuePositionAsc(UUID gameId, List<PerformanceState> states);
}
