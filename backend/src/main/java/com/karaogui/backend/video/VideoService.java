package com.karaogui.backend.video;

import com.karaogui.backend.auth.GameScopeException;
import com.karaogui.backend.auth.PlayerIdentity;
import com.karaogui.backend.auth.ScopeGuard;
import com.karaogui.backend.error.GameStateException;
import com.karaogui.backend.performance.YoutubeMetadataClient;
import com.karaogui.backend.player.Player;
import com.karaogui.backend.player.PlayerRepository;
import com.karaogui.backend.video.dto.ImportVideoRequest;
import com.karaogui.backend.video.dto.VideoDto;
import com.karaogui.backend.video.dto.VideoSearchResponse;
import jakarta.persistence.EntityNotFoundException;
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
    private final Clock clock;

    public VideoService(VideoRepository videoRepo, PlayerRepository playerRepo,
            YoutubeMetadataClient youtubeClient, Clock clock) {
        this.videoRepo = videoRepo;
        this.playerRepo = playerRepo;
        this.youtubeClient = youtubeClient;
        this.clock = clock;
    }

    @Transactional
    public VideoDto importVideo(PlayerIdentity identity, ImportVideoRequest req) {
        ScopeGuard.requirePlayerScope(identity, identity.gameId());
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

    @Transactional(readOnly = true)
    public VideoSearchResponse search(PlayerIdentity identity, String query, int page) {
        ScopeGuard.requireScope(identity, identity.gameId());
        int safePage = Math.max(0, page);
        // Fetch one extra row to determine hasMore without a separate count query.
        PageRequest req = PageRequest.of(0, safePage * PAGE_SIZE + PAGE_SIZE + 1);
        String q = query != null ? query.trim() : "";
        List<Video> rows = q.isEmpty()
                ? videoRepo.findAllByOrderByCreatedAtDesc(req)
                : videoRepo.search(q, req);

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
        Player player = playerRepo.findByIdAndGameId(identity.playerId(), identity.gameId())
                .orElseThrow(() -> new GameScopeException("FORBIDDEN", "Player not found in this game."));
        if (!player.isHost()) {
            throw new GameScopeException("FORBIDDEN", "Only the host can remove videos from the library.");
        }
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new EntityNotFoundException("Video not found: " + videoId));
        videoRepo.delete(video);
    }

    private VideoDto toDto(Video v) {
        return new VideoDto(v.getId(), v.getYoutubeUrl(), v.getVideoName(), v.getThumbnailUrl(),
                v.getSongTitle(), v.getArtist(), v.getDurationSeconds());
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
