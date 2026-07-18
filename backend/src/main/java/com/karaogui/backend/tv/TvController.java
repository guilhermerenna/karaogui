package com.karaogui.backend.tv;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tv")
class TvController {

    private final TvService tvService;

    TvController(TvService tvService) {
        this.tvService = tvService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    TvRegisterResponse register() {
        return tvService.register();
    }
}
