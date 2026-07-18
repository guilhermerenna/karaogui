package com.karaogui.backend.comment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.CommentLikeId> {
    boolean existsByIdCommentIdAndIdPlayerId(UUID commentId, UUID playerId);
    void deleteByIdCommentIdAndIdPlayerId(UUID commentId, UUID playerId);
}
