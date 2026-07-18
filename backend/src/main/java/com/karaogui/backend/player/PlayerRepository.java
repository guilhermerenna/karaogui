package com.karaogui.backend.player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    List<Player> findAllByGameIdOrderByScoreDesc(UUID gameId);

    Optional<Player> findByIdAndGameId(UUID id, UUID gameId);
}
