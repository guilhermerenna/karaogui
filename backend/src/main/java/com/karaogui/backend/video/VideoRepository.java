package com.karaogui.backend.video;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Optional<Video> findByYoutubeId(String youtubeId);

    @Query("""
            SELECT v FROM Video v
            WHERE LOWER(COALESCE(v.videoName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(v.songTitle, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(v.artist, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY v.createdAt DESC
            """)
    List<Video> search(@Param("q") String q, Pageable pageable);

    List<Video> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
