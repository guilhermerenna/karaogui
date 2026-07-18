package com.karaogui.backend.player;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSessionRepository extends JpaRepository<PlayerSession, UUID> {

    Optional<PlayerSession> findByTokenHash(String tokenHash);
}
