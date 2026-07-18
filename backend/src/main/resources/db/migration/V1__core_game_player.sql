-- game: root aggregate
CREATE TABLE game (
    id             uuid        PRIMARY KEY,
    join_code      varchar(6)  NOT NULL,
    state          text        NOT NULL DEFAULT 'CREATED'
                               CHECK (state IN ('CREATED', 'ACTIVE', 'OVER')),
    host_player_id uuid        NULL,     -- FK set after host player row exists
    created_at     timestamptz NOT NULL,
    started_at     timestamptz NULL,
    ended_at       timestamptz NULL,
    CONSTRAINT uq_game_join_code UNIQUE (join_code)
);

-- player: one row per participant per game
CREATE TABLE player (
    id                              uuid        PRIMARY KEY,
    game_id                         uuid        NOT NULL REFERENCES game(id),
    display_name                    text        NOT NULL,
    picture_url                     text        NULL,
    score                           integer     NOT NULL DEFAULT 0,
    performance_participation_count integer     NOT NULL DEFAULT 0,
    on_break_until                  timestamptz NULL,
    is_host                         boolean     NOT NULL DEFAULT false,
    joined_at                       timestamptz NOT NULL,
    version                         bigint      NOT NULL DEFAULT 0
);

CREATE INDEX idx_player_game_id    ON player (game_id);
CREATE INDEX idx_player_game_score ON player (game_id, score DESC);

-- add host FK now that player table exists
ALTER TABLE game
    ADD CONSTRAINT fk_game_host_player
    FOREIGN KEY (host_player_id) REFERENCES player(id);

-- player_session: interim disposable identity (token lives here, not on player)
CREATE TABLE player_session (
    id          uuid        PRIMARY KEY,
    player_id   uuid        NOT NULL UNIQUE REFERENCES player(id),
    game_id     uuid        NOT NULL REFERENCES game(id),
    token_hash  text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL
);
