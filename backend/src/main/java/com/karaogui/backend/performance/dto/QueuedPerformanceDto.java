package com.karaogui.backend.performance.dto;

import java.util.List;

public record QueuedPerformanceDto(
        Long performanceId,
        int queuePosition,
        String songTitle,
        String artist,
        String videoName,
        List<String> performerNames) {}
