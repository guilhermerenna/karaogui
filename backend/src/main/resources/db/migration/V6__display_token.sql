-- display token for the TV surface: one per game, no player association
CREATE TABLE game_display_token (
    id          uuid        PRIMARY KEY,
    game_id     uuid        NOT NULL UNIQUE REFERENCES game(id),
    token_hash  text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL
);
