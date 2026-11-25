package com.karaogui.backend.video;

import com.karaogui.backend.auth.PlayerIdentity;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes video library deletion when {@code karaogui.features.video-delete=true}.
 */
@RestController
@RequestMapping("/api/videos")
@ConditionalOnProperty(name = "karaogui.features.video-delete", havingValue = "true")
class VideoDeleteController {

    private final VideoService videoService;

    VideoDeleteController(VideoService videoService) {
        this.videoService = videoService;
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(PlayerIdentity identity, @PathVariable UUID videoId) {
        videoService.deleteVideo(identity, videoId);
    }
}
