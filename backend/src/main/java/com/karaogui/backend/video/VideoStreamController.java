package com.karaogui.backend.video;

import com.karaogui.backend.config.KaraoguiProperties;
import com.karaogui.backend.video.storage.VideoStorage;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestController
@RequestMapping("/api/videos")
class VideoStreamController {

    private static final MediaType DEFAULT_VIDEO_MEDIA_TYPE = MediaType.valueOf("video/mp4");

    private final VideoRepository videoRepo;
    private final VideoStorage videoStorage;
    private final KaraoguiProperties props;

    VideoStreamController(VideoRepository videoRepo, VideoStorage videoStorage,
            KaraoguiProperties props) {
        this.videoRepo = videoRepo;
        this.videoStorage = videoStorage;
        this.props = props;
    }

    @GetMapping("/{videoId}/stream")
    ResponseEntity<ResourceRegion> stream(@PathVariable UUID videoId,
            @RequestHeader HttpHeaders headers) throws IOException {
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new EntityNotFoundException("Video not found: " + videoId));
        if (video.getLocalPath() == null || video.getLocalPath().isBlank()) {
            throw new EntityNotFoundException("No local video file configured for: " + videoId);
        }

        Resource resource = videoStorage.resolve(video.getLocalPath())
                .orElseThrow(() -> new EntityNotFoundException("Local video file not found for: " + videoId));

        long contentLength = resource.contentLength();
        if (contentLength <= 0) {
            throw new EntityNotFoundException("Local video file is empty for: " + videoId);
        }

        List<HttpRange> ranges;
        try {
            ranges = headers.getRange();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        long chunkSize = Math.max(1L, props.videos().streamChunkSize().toBytes());
        ResourceRegion region;
        if (ranges.isEmpty()) {
            region = new ResourceRegion(resource, 0, Math.min(chunkSize, contentLength));
        } else {
            HttpRange range = ranges.getFirst();
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            if (start < 0 || start >= contentLength || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                        .build();
            }
            long rangeLength = Math.min(chunkSize, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(DEFAULT_VIDEO_MEDIA_TYPE);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(region);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleClientAbort() {
        // Browsers routinely abort range requests when seeking or buffering.
    }
}
