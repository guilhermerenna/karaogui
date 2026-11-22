-- 1. Support local video file paths (relative to karaogui.videos.root-dir)
--    and allow local-only library entries with no YouTube link.
ALTER TABLE video
    ADD COLUMN local_path text NULL,
    ALTER COLUMN youtube_id DROP NOT NULL,
    ALTER COLUMN youtube_url DROP NOT NULL;

ALTER TABLE video
    ADD CONSTRAINT chk_video_has_source
        CHECK (youtube_url IS NOT NULL OR local_path IS NOT NULL);

CREATE UNIQUE INDEX uq_video_local_path
    ON video (local_path)
    WHERE local_path IS NOT NULL;

-- 2. Retain reference from performance -> video so DTOs & realtime events can
--    expose the streamable video ID. ON DELETE SET NULL keeps host video
--    deletion safe even after a video has been played in past performances.
ALTER TABLE performance
    ADD COLUMN video_id uuid NULL REFERENCES video(id) ON DELETE SET NULL;

CREATE INDEX idx_performance_video_id
    ON performance (video_id)
    WHERE video_id IS NOT NULL;
