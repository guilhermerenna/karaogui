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
class GameLifecycleController {

    private final GameService gameService;

    GameLifecycleController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/api/games/by-code/{joinCode}")
    GameSnapshotDto snapshotByCode(@PathVariable String joinCode, PlayerIdentity identity) {
        return gameService.getSnapshotByCode(joinCode, identity);
    }

    @GetMapping("/api/games/{gameId}")
    GameSnapshotDto snapshot(@PathVariable UUID gameId, PlayerIdentity identity) {
        return gameService.getSnapshot(gameId, identity);
    }

    @PostMapping("/api/games/{gameId}/start")
    GameSnapshotDto start(@PathVariable UUID gameId, PlayerIdentity identity) {
        return gameService.startGame(gameId, identity);
    }

    @PostMapping("/api/games/{gameId}/end")
    void end(@PathVariable UUID gameId, PlayerIdentity identity) {
        gameService.endGame(gameId, identity);
    }
}
