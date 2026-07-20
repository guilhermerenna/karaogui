-- video: a global, cross-game library of importable YouTube videos.
-- duration_seconds is the same value used to compute the judging deadline once queued.
CREATE TABLE video (
    id                   uuid PRIMARY KEY,
    youtube_id           text NOT NULL UNIQUE,
    youtube_url          text NOT NULL,
    video_name           text NULL,
    thumbnail_url        text NULL,
    song_title           text NULL,
    artist               text NULL,
    duration_seconds     bigint NULL,
    created_at           timestamptz NOT NULL,
    created_by_player_id uuid NULL,
    version              bigint NOT NULL DEFAULT 0
);

CREATE INDEX idx_video_search ON video (video_name, song_title, artist);
