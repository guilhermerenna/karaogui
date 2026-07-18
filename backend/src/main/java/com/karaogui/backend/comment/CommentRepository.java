package com.karaogui.backend.comment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByGameIdOrderByCreatedAtDesc(UUID gameId, Pageable pageable);
}
