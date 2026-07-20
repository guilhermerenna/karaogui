package com.karaogui.backend.performance;

import com.karaogui.backend.config.KaraoguiProperties;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Lookup of YouTube video metadata via the Data API v3 videos.list endpoint.
 *
 * <p>Distinguishes three outcomes so callers can enforce link validation without making the
 * API a hard dependency:
 * <ul>
 *   <li>{@code ok} — the API gave a definite answer for a real, embeddable video (with duration).
 *   <li>{@code rejected} — a definite bad-video verdict: the URL has no parseable id, the video
 *       does not exist (empty items), or {@code status.embeddable == false}.
 *   <li>{@code unavailable} — the API could not be consulted (missing key, quota, network/5xx).
 *       Callers should let queueing proceed with no duration in this case.
 * </ul>
 */
@Component
public class YoutubeMetadataClient {

    private static final Logger log = LoggerFactory.getLogger(YoutubeMetadataClient.class);
    private static final String ENDPOINT = "https://www.googleapis.com/youtube/v3/videos";

    private static final Pattern[] ID_PATTERNS = {
            Pattern.compile("youtu\\.be/([^?&/]+)"),
            Pattern.compile("youtube\\.com/watch\\?v=([^&]+)"),
            Pattern.compile("youtube\\.com/embed/([^?&/]+)"),
    };

    private final RestClient restClient;
    private final String apiKey;

    public YoutubeMetadataClient(KaraoguiProperties props) {
        this.apiKey = props.youtube() != null ? props.youtube().apiKey() : null;
        this.restClient = RestClient.create();
    }

    public LookupResult fetch(String youtubeUrl) {
        String videoId = extractId(youtubeUrl);
        if (videoId == null) {
            return LookupResult.rejected("NOT_A_YOUTUBE_VIDEO");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return LookupResult.unavailable();
        }
        try {
            VideoListResponse resp = restClient.get()
                    .uri(ENDPOINT + "?part=contentDetails,status,snippet&id={id}&key={key}", videoId, apiKey)
                    .retrieve()
                    .body(VideoListResponse.class);
            if (resp == null || resp.items() == null || resp.items().isEmpty()) {
                return LookupResult.rejected("VIDEO_NOT_FOUND");
            }
            Item item = resp.items().get(0);
            boolean embeddable = item.status() == null || item.status().embeddable() == null
                    || item.status().embeddable();
            if (!embeddable) {
                return LookupResult.rejected("VIDEO_NOT_EMBEDDABLE");
            }
            Long seconds = item.contentDetails() != null
                    ? parseDuration(item.contentDetails().duration())
                    : null;
            String title = item.snippet() != null ? item.snippet().title() : null;
            String thumbnailUrl = pickThumbnail(item.snippet());
            return LookupResult.ok(new VideoMetadata(seconds, true, title, thumbnailUrl));
        } catch (Exception e) {
            log.warn("YouTube metadata lookup failed for id={}: {}", videoId, e.getMessage());
            return LookupResult.unavailable();
        }
    }

    private static String pickThumbnail(Snippet snippet) {
        if (snippet == null || snippet.thumbnails() == null) {
            return null;
        }
        Thumbnails t = snippet.thumbnails();
        if (t.high() != null && t.high().url() != null) {
            return t.high().url();
        }
        if (t.medium() != null && t.medium().url() != null) {
            return t.medium().url();
        }
        return t.defaultThumb() != null ? t.defaultThumb().url() : null;
    }

    public static String extractId(String url) {
        if (url == null) {
            return null;
        }
        for (Pattern p : ID_PATTERNS) {
            Matcher m = p.matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    static Long parseDuration(String iso8601) {
        if (iso8601 == null || iso8601.isBlank()) {
            return null;
        }
        try {
            return Duration.parse(iso8601).toSeconds();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public enum Outcome { OK, REJECTED, UNAVAILABLE }

    /**
     * Outcome of a metadata lookup. {@code metadata} is present only when {@code status == OK};
     * {@code rejectReason} is present only when {@code status == REJECTED}.
     */
    public record LookupResult(Outcome status, VideoMetadata metadata, String rejectReason) {

        static LookupResult ok(VideoMetadata metadata) {
            return new LookupResult(Outcome.OK, metadata, null);
        }

        static LookupResult rejected(String reason) {
            return new LookupResult(Outcome.REJECTED, null, reason);
        }

        static LookupResult unavailable() {
            return new LookupResult(Outcome.UNAVAILABLE, null, null);
        }

        public boolean isRejected() {
            return status == Outcome.REJECTED;
        }
    }

    public record VideoMetadata(Long durationSeconds, boolean embeddable, String title, String thumbnailUrl) {}

    record VideoListResponse(List<Item> items) {}

    record Item(ContentDetails contentDetails, VideoStatus status, Snippet snippet) {}

    record ContentDetails(String duration) {}

    record VideoStatus(Boolean embeddable) {}

    record Snippet(String title, Thumbnails thumbnails) {}

    record Thumbnails(
            @com.fasterxml.jackson.annotation.JsonProperty("default") Thumbnail defaultThumb,
            Thumbnail medium,
            Thumbnail high) {}

    record Thumbnail(String url) {}
}
