package com.karaogui.backend.player;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDisplayTokenRepository extends JpaRepository<GameDisplayToken, UUID> {

    Optional<GameDisplayToken> findByTokenHash(String tokenHash);
}
