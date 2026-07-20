package com.karaogui.backend.performance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurrentPerformanceDto(
        Long performanceId,
        String type,
        String state,
        String youtubeUrl,
        Instant confirmDeadlineAt,
        Instant replacementOpensAt,
        List<SlotDto> slots,
        List<UUID> judgePlayerIds,
        Long durationSeconds,
        Instant judgingDeadlineAt,
        Instant startedAt) {}
