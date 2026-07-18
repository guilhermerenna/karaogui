package com.karaogui.backend.game.dto;

import java.util.UUID;

public record CreateGameResponse(
        UUID gameId,
        String joinCode,
        String joinCodeDisplay,
        String state,
        PlayerInfo you,
        String sessionToken,
        String displayToken) {

    public record PlayerInfo(UUID playerId, String displayName, boolean isHost) {}
}
