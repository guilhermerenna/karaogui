package com.karaogui.backend.performance.dto;

import java.util.List;
import java.util.UUID;

public record QueuePerformanceRequest(String type, UUID videoId, String youtubeUrl, List<UUID> performerPlayerIds, Integer slotCount) {}
