package com.karaogui.backend.game;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByJoinCode(String joinCode);
}
