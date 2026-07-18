package com.karaogui.backend.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comment")
public class Comment {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "performance_id")
    private Long performanceId;

    @Column(name = "author_player_id", nullable = false)
    private UUID authorPlayerId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Version
    private long version;

    protected Comment() {}

    public Comment(UUID id, UUID gameId, Long performanceId, UUID authorPlayerId,
            String body, Instant createdAt) {
        this.id = id;
        this.gameId = gameId;
        this.performanceId = performanceId;
        this.authorPlayerId = authorPlayerId;
        this.body = body;
        this.createdAt = createdAt;
        this.likeCount = 0;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public Long getPerformanceId() { return performanceId; }
    public UUID getAuthorPlayerId() { return authorPlayerId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public int getLikeCount() { return likeCount; }

    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
}
