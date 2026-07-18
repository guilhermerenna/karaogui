package com.karaogui.backend.performance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PerformanceDetailDto(
        Long performanceId,
        String type,
        String state,
        String youtubeUrl,
        Instant confirmDeadlineAt,
        Instant replacementOpensAt,
        List<SlotDto> slots,
        List<JudgeAssignmentDto> judges,
        boolean myEvaluationSubmitted,
        boolean myRatingSubmitted) {}
