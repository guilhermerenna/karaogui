package com.karaogui.backend.video.dto;

import java.util.UUID;

public record RegisterLocalVideoRequest(
        UUID videoId,
        String videoUrl,
        String songTitle,
        String artist,
        String videoName,
        Long durationSeconds,
        String youtubeUrl) {}
