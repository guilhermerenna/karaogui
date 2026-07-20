package com.karaogui.backend.video.dto;

import java.util.List;

public record VideoSearchResponse(List<VideoDto> content, boolean hasMore) {}
