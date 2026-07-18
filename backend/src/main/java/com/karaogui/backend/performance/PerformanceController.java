package com.karaogui.backend.performance;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.performance.dto.EvaluateRequest;
import com.karaogui.backend.performance.dto.PerformanceDetailDto;
import com.karaogui.backend.performance.dto.QueuePerformanceRequest;
import com.karaogui.backend.performance.dto.RateRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/performances")
class PerformanceController {

    private final PerformanceService performanceService;

    PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PerformanceDetailDto queue(@PathVariable UUID gameId, PlayerIdentity identity,
            @RequestBody QueuePerformanceRequest req) {
        return performanceService.queuePerformance(gameId, identity, req);
    }

    @GetMapping("/{performanceId}")
    PerformanceDetailDto detail(@PathVariable UUID gameId, @PathVariable Long performanceId,
            PlayerIdentity identity) {
        return performanceService.getDetail(gameId, performanceId, identity);
    }

    @PostMapping("/{performanceId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@PathVariable UUID gameId, @PathVariable Long performanceId,
            PlayerIdentity identity) {
        performanceService.confirmSlot(gameId, performanceId, identity);
    }

    @PostMapping("/{performanceId}/volunteer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void volunteer(@PathVariable UUID gameId, @PathVariable Long performanceId,
            PlayerIdentity identity) {
        performanceService.volunteerSlot(gameId, performanceId, identity);
    }

    @PostMapping("/{performanceId}/evaluate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void evaluate(@PathVariable UUID gameId, @PathVariable Long performanceId,
            PlayerIdentity identity, @RequestBody EvaluateRequest req) {
        performanceService.submitEvaluation(gameId, performanceId, identity, req);
    }

    @PostMapping("/{performanceId}/rate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@PathVariable UUID gameId, @PathVariable Long performanceId,
            PlayerIdentity identity, @RequestBody RateRequest req) {
        performanceService.submitRating(gameId, performanceId, identity, req);
    }
}
