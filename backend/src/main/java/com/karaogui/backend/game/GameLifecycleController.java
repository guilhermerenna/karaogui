package com.karaogui.backend.game;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.game.dto.GameSnapshotDto;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}")
class GameLifecycleController {

    private final GameService gameService;

    GameLifecycleController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    GameSnapshotDto snapshot(@PathVariable UUID gameId, PlayerIdentity identity) {
        return gameService.getSnapshot(gameId, identity);
    }

    @PostMapping("/start")
    GameSnapshotDto start(@PathVariable UUID gameId, PlayerIdentity identity) {
        return gameService.startGame(gameId, identity);
    }
}
