package com.karaogui.backend.performance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurrentPerformanceDto(
        Long performanceId,
        String type,
        String state,
        UUID videoId,
        String youtubeUrl,
        String songTitle,
        String artist,
        String videoName,
        Instant confirmDeadlineAt,
        Instant replacementOpensAt,
        List<SlotDto> slots,
        List<UUID> judgePlayerIds,
        List<JudgeAssignmentDto> judges,
        Long durationSeconds,
        Instant judgingDeadlineAt,
        Instant startedAt) {}
