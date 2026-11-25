package com.karaogui.backend.video;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.video.dto.ImportVideoRequest;
import com.karaogui.backend.video.dto.VideoDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes YouTube video import when {@code karaogui.features.youtube=true}.
 */
@RestController
@RequestMapping("/api/videos")
@ConditionalOnProperty(name = "karaogui.features.youtube", havingValue = "true")
class YoutubeVideoController {

    private final VideoService videoService;

    YoutubeVideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VideoDto importVideo(PlayerIdentity identity, @RequestBody ImportVideoRequest req) {
        return videoService.importVideo(identity, req);
    }
}
