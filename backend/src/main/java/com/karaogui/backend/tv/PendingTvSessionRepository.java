package com.karaogui.backend.tv;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingTvSessionRepository extends JpaRepository<PendingTvSession, String> {
    Optional<PendingTvSession> findByDisplayTokenHash(String displayTokenHash);
}
