package com.karaogui.backend.performance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformerSlotRepository extends JpaRepository<PerformerSlot, UUID> {

    List<PerformerSlot> findByPerformanceIdOrderBySlotIndex(Long performanceId);

    Optional<PerformerSlot> findByPerformanceIdAndCurrentPlayerId(Long performanceId, UUID playerId);
}
