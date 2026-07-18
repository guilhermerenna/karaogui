package com.karaogui.backend.performance;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, RatingId> {

    Optional<Rating> findById(RatingId id);
}
