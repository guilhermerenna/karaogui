package com.karaogui.backend.performance.dto;

import java.util.List;
import java.util.UUID;

public record QueuePerformanceRequest(String type, String youtubeUrl, List<UUID> performerPlayerIds) {}
