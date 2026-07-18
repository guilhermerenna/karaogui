package com.karaogui.backend.performance.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvaluateRequest(
        List<CriterionScore> baseline,
        Map<UUID, List<CriterionScore>> perPerformer) {}
