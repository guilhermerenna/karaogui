package com.karaogui.backend.performance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RatingScoreRepository extends JpaRepository<RatingScore, RatingScoreId> {

    List<RatingScore> findByIdPerformanceIdAndIdPlayerId(Long performanceId, UUID playerId);

    List<RatingScore> findByIdPerformanceIdAndIdCriterion(Long performanceId, String criterion);

    @Modifying
    @Query("DELETE FROM RatingScore rs WHERE rs.id.performanceId = :performanceId AND rs.id.playerId = :playerId")
    void deleteByPerformanceIdAndPlayerId(Long performanceId, UUID playerId);
}
