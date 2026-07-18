CREATE TABLE pending_tv_session (
    join_code          VARCHAR(6)   PRIMARY KEY,
    display_token_hash TEXT         NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ  NOT NULL
);
