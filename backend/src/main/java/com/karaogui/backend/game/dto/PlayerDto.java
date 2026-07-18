package com.karaogui.backend.game.dto;

import java.time.Instant;
import java.util.UUID;

public record PlayerDto(
        UUID playerId,
        String displayName,
        String pictureUrl,
        int score,
        boolean isHost,
        boolean onBreak) {}
