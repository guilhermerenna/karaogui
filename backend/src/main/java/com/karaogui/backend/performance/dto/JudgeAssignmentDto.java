package com.karaogui.backend.performance.dto;

import java.util.UUID;

public record JudgeAssignmentDto(UUID judgePlayerId, String displayName, boolean submitted) {}
