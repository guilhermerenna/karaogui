package com.karaogui.backend.performance.dto;

import java.util.UUID;

public record SlotDto(
        UUID slotId,
        int slotIndex,
        UUID currentPlayerId,
        String currentPlayerName,
        String state) {}
