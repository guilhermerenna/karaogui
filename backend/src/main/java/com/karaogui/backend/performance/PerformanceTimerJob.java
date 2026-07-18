package com.karaogui.backend.performance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PerformanceTimerJob {

    private final PerformanceRepository performanceRepo;
    private final PerformanceService performanceService;
    private final Clock clock;

    public PerformanceTimerJob(PerformanceRepository performanceRepo,
            PerformanceService performanceService, Clock clock) {
        this.performanceRepo = performanceRepo;
        this.performanceService = performanceService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        Instant now = clock.instant();
        List<Performance> expired = performanceRepo
                .findByStateAndConfirmDeadlineAtBefore(PerformanceState.CONFIRMING, now);
        for (Performance p : expired) {
            performanceService.skipPerformance(p.getId());
        }
    }
}
