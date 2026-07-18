package com.karaogui.backend.game.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GameSnapshotDto(
        UUID gameId,
        String joinCode,
        String joinCodeDisplay,
        String state,
        List<PlayerDto> players,
        Map<String, Object> currentPerformance,
        RankingPageDto ranking) {}
