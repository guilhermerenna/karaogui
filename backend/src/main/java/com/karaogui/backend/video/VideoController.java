package com.karaogui.backend.video;

import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.video.dto.ImportVideoRequest;
import com.karaogui.backend.video.dto.VideoDto;
import com.karaogui.backend.video.dto.VideoSearchResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
class VideoController {

    private final VideoService videoService;

    VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    VideoSearchResponse search(PlayerIdentity identity,
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
        return videoService.search(identity, q, page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VideoDto importVideo(PlayerIdentity identity, @RequestBody ImportVideoRequest req) {
        return videoService.importVideo(identity, req);
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(PlayerIdentity identity, @PathVariable UUID videoId) {
        videoService.deleteVideo(identity, videoId);
    }
}
