package com.karaogui.backend.video;

import com.karaogui.backend.auth.GameScopeException;
import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.auth.ScopeGuard;
import com.karaogui.backend.config.KaraoguiProperties;
import com.karaogui.backend.error.GameStateException;
import com.karaogui.backend.performance.YoutubeMetadataClient;
import com.karaogui.backend.player.Player;
import com.karaogui.backend.player.PlayerRepository;
import com.karaogui.backend.video.dto.ImportVideoRequest;
import com.karaogui.backend.video.dto.RegisterLocalVideoRequest;
import com.karaogui.backend.video.dto.VideoDto;
import com.karaogui.backend.video.dto.VideoSearchResponse;
import com.karaogui.backend.video.storage.VideoStorage;
import jakarta.persistence.EntityNotFoundException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoService {

    static final int PAGE_SIZE = 6;

    private final VideoRepository videoRepo;
    private final PlayerRepository playerRepo;
    private final YoutubeMetadataClient youtubeClient;
    private final VideoStorage videoStorage;
    private final KaraoguiProperties props;
    private final Clock clock;

    public VideoService(VideoRepository videoRepo, PlayerRepository playerRepo,
            YoutubeMetadataClient youtubeClient, VideoStorage videoStorage,
            KaraoguiProperties props, Clock clock) {
        this.videoRepo = videoRepo;
        this.playerRepo = playerRepo;
        this.youtubeClient = youtubeClient;
        this.videoStorage = videoStorage;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public VideoDto importVideo(PlayerIdentity identity, ImportVideoRequest req) {
        ScopeGuard.requirePlayerScope(identity, identity.gameId());
        if (!isYoutubeEnabled()) {
            throw new GameStateException("YOUTUBE_DISABLED", "YouTube video import is currently disabled.");
        }
        String url = req.youtubeUrl() != null ? req.youtubeUrl().trim() : "";
        String youtubeId = YoutubeMetadataClient.extractId(url);
        if (youtubeId == null) {
            throw new GameStateException("NOT_A_YOUTUBE_VIDEO",
                    "This YouTube link can't be used. Please check the video and try another.");
        }

        String songTitle = blankToNull(req.songTitle());
        String artist = blankToNull(req.artist());

        // Idempotent import: an already-imported video is reused; only fill in newly-provided metadata.
        var existing = videoRepo.findByYoutubeId(youtubeId);
        if (existing.isPresent()) {
            Video video = existing.get();
            if (songTitle != null) {
                video.setSongTitle(songTitle);
            }
            if (artist != null) {
                video.setArtist(artist);
            }
            return toDto(videoRepo.save(video));
        }

        YoutubeMetadataClient.LookupResult metadata = youtubeClient.fetch(url);
        if (metadata.isRejected()) {
            throw new GameStateException(metadata.rejectReason(),
                    "This YouTube link can't be used. Please check the video and try another.");
        }

        String videoName = null;
        String thumbnailUrl = null;
        Long durationSeconds = null;
        if (metadata.metadata() != null) {
            videoName = blankToNull(metadata.metadata().title());
            thumbnailUrl = blankToNull(metadata.metadata().thumbnailUrl());
            durationSeconds = metadata.metadata().durationSeconds();
        }

        Video video = new Video(UUID.randomUUID(), youtubeId, url, videoName, thumbnailUrl,
                songTitle, artist, durationSeconds, clock.instant(), identity.playerId());
        return toDto(videoRepo.save(video));
    }

    @Transactional
    public VideoDto registerLocalVideo(RegisterLocalVideoRequest req) {
        String localPath;
        try {
            localPath = videoStorage.normalizeToRelativePath(req.videoUrl());
        } catch (IllegalArgumentException e) {
            throw new GameStateException("INVALID_VIDEO_PATH", e.getMessage());
        }

        String songTitle = blankToNull(req.songTitle());
        String artist = blankToNull(req.artist());
        String videoName = blankToNull(req.videoName());
        Long durationSeconds = req.durationSeconds();

        if (req.videoId() != null) {
            Video existing = videoRepo.findById(req.videoId())
                    .orElseThrow(() -> new EntityNotFoundException("Video not found: " + req.videoId()));
            existing.setLocalPath(localPath);
            applyOptionalMetadata(existing, videoName, songTitle, artist, durationSeconds);
            return toDto(videoRepo.save(existing));
        }

        String youtubeUrl = blankToNull(req.youtubeUrl());
        String youtubeId = null;
        if (youtubeUrl != null) {
            youtubeId = YoutubeMetadataClient.extractId(youtubeUrl);
            if (youtubeId == null) {
                throw new GameStateException("NOT_A_YOUTUBE_VIDEO",
                        "This YouTube link can't be used. Please check the video and try another.");
            }
        }

        var byPath = videoRepo.findByLocalPath(localPath);
        if (byPath.isPresent()) {
            Video existing = byPath.get();
            applyOptionalMetadata(existing, videoName, songTitle, artist, durationSeconds);
            return toDto(videoRepo.save(existing));
        }

        if (youtubeId != null) {
            var byYoutube = videoRepo.findByYoutubeId(youtubeId);
            if (byYoutube.isPresent()) {
                Video existing = byYoutube.get();
                existing.setLocalPath(localPath);
                applyOptionalMetadata(existing, videoName, songTitle, artist, durationSeconds);
                return toDto(videoRepo.save(existing));
            }
        }

        String resolvedName = videoName != null ? videoName : defaultNameFromPath(localPath);
        Video video = new Video(UUID.randomUUID(), youtubeId, youtubeUrl, localPath, resolvedName,
                null, songTitle, artist, durationSeconds, clock.instant(), null);
        return toDto(videoRepo.save(video));
    }

    @Transactional(readOnly = true)
    public VideoSearchResponse search(PlayerIdentity identity, String query, int page) {
        ScopeGuard.requireScope(identity, identity.gameId());
        int safePage = Math.max(0, page);
        // Fetch one extra row to determine hasMore without a separate count query.
        PageRequest req = PageRequest.of(0, safePage * PAGE_SIZE + PAGE_SIZE + 1);
        String q = query != null ? query.trim() : "";
        List<Video> rows;
        if (isYoutubeEnabled()) {
            rows = q.isEmpty()
                    ? videoRepo.findAllByOrderByCreatedAtDesc(req)
                    : videoRepo.search(q, req);
        } else {
            rows = q.isEmpty()
                    ? videoRepo.findAllLocalByOrderByCreatedAtDesc(req)
                    : videoRepo.searchLocal(q, req);
        }

        int from = safePage * PAGE_SIZE;
        if (from >= rows.size()) {
            return new VideoSearchResponse(List.of(), false);
        }
        int to = Math.min(from + PAGE_SIZE, rows.size());
        boolean hasMore = rows.size() > to;
        List<VideoDto> content = rows.subList(from, to).stream().map(this::toDto).toList();
        return new VideoSearchResponse(content, hasMore);
    }

    @Transactional
    public void deleteVideo(PlayerIdentity identity, UUID videoId) {
        ScopeGuard.requirePlayerScope(identity, identity.gameId());
        if (!isVideoDeleteEnabled()) {
            throw new GameStateException("VIDEO_DELETE_DISABLED", "Deleting videos is currently disabled.");
        }
        Player player = playerRepo.findByIdAndGameId(identity.playerId(), identity.gameId())
                .orElseThrow(() -> new GameScopeException("FORBIDDEN", "Player not found in this game."));
        if (!player.isHost()) {
            throw new GameScopeException("FORBIDDEN", "Only the host can remove videos from the library.");
        }
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new EntityNotFoundException("Video not found: " + videoId));
        videoRepo.delete(video);
    }

    private boolean isYoutubeEnabled() {
        return props.features() != null && props.features().youtube();
    }

    private boolean isVideoDeleteEnabled() {
        return props.features() != null && props.features().videoDelete();
    }

    private VideoDto toDto(Video v) {
        boolean hasLocalFile = v.getLocalPath() != null && !v.getLocalPath().isBlank();
        return new VideoDto(v.getId(), v.getYoutubeUrl(), hasLocalFile, v.getVideoName(),
                v.getThumbnailUrl(), v.getSongTitle(), v.getArtist(), v.getDurationSeconds());
    }

    private static void applyOptionalMetadata(Video video, String videoName, String songTitle,
            String artist, Long durationSeconds) {
        if (videoName != null) {
            video.setVideoName(videoName);
        }
        if (songTitle != null) {
            video.setSongTitle(songTitle);
        }
        if (artist != null) {
            video.setArtist(artist);
        }
        if (durationSeconds != null) {
            video.setDurationSeconds(durationSeconds);
        }
    }

    private static String defaultNameFromPath(String localPath) {
        Path fileName = Path.of(localPath).getFileName();
        if (fileName == null) {
            return localPath;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
