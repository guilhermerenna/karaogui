package com.karaogui.backend.comment.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentDto(
        UUID commentId,
        UUID authorPlayerId,
        String authorName,
        String body,
        Instant createdAt,
        int likeCount) {}
