package com.karaogui.backend.game;

import com.karaogui.backend.game.dto.RankingPageDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameEvent(long seq, String type, Instant at, Object data) {

    public record PlayerJoinedData(UUID playerId, String displayName) {}

    public record GameStartedData(UUID gameId) {}

    public record RankingUpdatedData(List<RankingPageDto.Entry> entries, int totalPlayers) {}
}
