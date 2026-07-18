package com.karaogui.backend.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "comment_like")
public class CommentLike {

    @EmbeddedId
    private CommentLikeId id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommentLike() {}

    public CommentLike(UUID commentId, UUID playerId, UUID gameId, Instant createdAt) {
        this.id = new CommentLikeId(commentId, playerId);
        this.gameId = gameId;
        this.createdAt = createdAt;
    }

    public CommentLikeId getId() { return id; }
    public UUID getGameId() { return gameId; }

    @Embeddable
    public static class CommentLikeId implements Serializable {
        @Column(name = "comment_id", nullable = false)
        private UUID commentId;

        @Column(name = "player_id", nullable = false)
        private UUID playerId;

        protected CommentLikeId() {}

        public CommentLikeId(UUID commentId, UUID playerId) {
            this.commentId = commentId;
            this.playerId = playerId;
        }

        public UUID getCommentId() { return commentId; }
        public UUID getPlayerId() { return playerId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CommentLikeId c)) return false;
            return Objects.equals(commentId, c.commentId) && Objects.equals(playerId, c.playerId);
        }

        @Override
        public int hashCode() { return Objects.hash(commentId, playerId); }
    }
}
