package com.karaogui.backend.game.dto;

import java.util.List;
import java.util.UUID;

public record RankingPageDto(
        int page,
        int pageSize,
        int totalPlayers,
        List<Entry> entries) {

    public record Entry(int rank, UUID playerId, String displayName, int score) {}
}
