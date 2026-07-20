package com.karaogui.backend.video.dto;

import java.util.UUID;

public record VideoDto(
        UUID videoId,
        String youtubeUrl,
        String videoName,
        String thumbnailUrl,
        String songTitle,
        String artist,
        Long durationSeconds) {}
