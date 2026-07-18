package com.karaogui.backend.performance.dto;

import java.util.UUID;

public record ScoreResultDto(UUID playerId, String displayName, int points) {}
