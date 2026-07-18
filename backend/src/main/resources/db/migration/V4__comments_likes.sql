-- comment: live message on a performance
CREATE TABLE comment (
    id               uuid        PRIMARY KEY,
    game_id          uuid        NOT NULL REFERENCES game(id),
    performance_id   bigint      NOT NULL REFERENCES performance(id),
    author_player_id uuid        NOT NULL REFERENCES player(id),
    body             text        NOT NULL,
    created_at       timestamptz NOT NULL,
    like_count       integer     NOT NULL DEFAULT 0,
    version          bigint      NOT NULL DEFAULT 0  -- optimistic locking (like_count updates)
);

CREATE INDEX idx_comment_performance_feed ON comment (performance_id, created_at);

-- comment_like: one like per player per comment; composite PK enforces uniqueness
CREATE TABLE comment_like (
    comment_id  uuid        NOT NULL REFERENCES comment(id),
    player_id   uuid        NOT NULL REFERENCES player(id),
    game_id     uuid        NOT NULL REFERENCES game(id),
    created_at  timestamptz NOT NULL,
    PRIMARY KEY (comment_id, player_id)
);
