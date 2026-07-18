package com.karaogui.backend.game;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.game.dto.PlayerDto;
import com.karaogui.backend.game.dto.RankingPageDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}")
class PlayerController {

    private final GameService gameService;

    PlayerController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/players")
    List<PlayerDto> listPlayers(@PathVariable UUID gameId, PlayerIdentity identity) {
        return gameService.listPlayers(gameId, identity);
    }

    @GetMapping("/players/me")
    PlayerDto me(@PathVariable UUID gameId, PlayerIdentity identity) {
        return gameService.getMe(gameId, identity);
    }

    @GetMapping("/ranking")
    RankingPageDto ranking(@PathVariable UUID gameId, PlayerIdentity identity,
            @RequestParam(defaultValue = "0") int page) {
        return gameService.getRankingPage(gameId, identity, page);
    }
}
