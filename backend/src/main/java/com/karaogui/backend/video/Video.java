package com.karaogui.backend.video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video")
public class Video {

    @Id
    private UUID id;

    @Column(name = "youtube_id", nullable = false, unique = true)
    private String youtubeId;

    @Column(name = "youtube_url", nullable = false)
    private String youtubeUrl;

    @Column(name = "video_name")
    private String videoName;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "song_title")
    private String songTitle;

    @Column(name = "artist")
    private String artist;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_player_id")
    private UUID createdByPlayerId;

    @Version
    private long version;

    protected Video() {}

    public Video(UUID id, String youtubeId, String youtubeUrl, String videoName, String thumbnailUrl,
            String songTitle, String artist, Long durationSeconds, Instant createdAt, UUID createdByPlayerId) {
        this.id = id;
        this.youtubeId = youtubeId;
        this.youtubeUrl = youtubeUrl;
        this.videoName = videoName;
        this.thumbnailUrl = thumbnailUrl;
        this.songTitle = songTitle;
        this.artist = artist;
        this.durationSeconds = durationSeconds;
        this.createdAt = createdAt;
        this.createdByPlayerId = createdByPlayerId;
    }

    public UUID getId() { return id; }
    public String getYoutubeId() { return youtubeId; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getVideoName() { return videoName; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getSongTitle() { return songTitle; }
    public String getArtist() { return artist; }
    public Long getDurationSeconds() { return durationSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedByPlayerId() { return createdByPlayerId; }

    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }
    public void setArtist(String artist) { this.artist = artist; }
}
