package com.karaogui.backend.game;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.game.dto.CreateGameRequest;
import com.karaogui.backend.game.dto.CreateGameResponse;
import com.karaogui.backend.game.dto.JoinGameRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
class GameController {

    private final GameService gameService;

    GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateGameResponse create(@Valid @RequestBody CreateGameRequest req) {
        return gameService.createGame(req);
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    CreateGameResponse join(@Valid @RequestBody JoinGameRequest req) {
        return gameService.joinGame(req);
    }
}
