package com.karaogui.backend.game.dto;

import com.karaogui.backend.performance.dto.CurrentPerformanceDto;
import java.util.List;
import java.util.UUID;

public record GameSnapshotDto(
        UUID gameId,
        String joinCode,
        String joinCodeDisplay,
        String state,
        List<PlayerDto> players,
        CurrentPerformanceDto currentPerformance,
        RankingPageDto ranking,
        boolean queueNonEmpty) {}
