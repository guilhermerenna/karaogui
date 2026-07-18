-- performance: a single scored act; PK is a global bigint; user-visible number is game_local_number
CREATE TABLE performance (
    id                  bigint  PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    game_id             uuid    NOT NULL REFERENCES game(id),
    type                text    NOT NULL
                                CHECK (type IN ('KARAOKE','MAGIC_TRICK','DANCE','ACTING_MIMIC',
                                                'STANDUP','TRIVIA','REVERSE_MIMIC','PHYSICAL_CHALLENGE')),
    physical_kind       text    NULL
                                CHECK (physical_kind IN ('BALLOON','WATER','MARSHMALLOW','COOKIE')),
    author_player_id    uuid    NULL REFERENCES player(id),
    state               text    NOT NULL DEFAULT 'QUEUED'
                                CHECK (state IN ('QUEUED','ANNOUNCED','CONFIRMING','RUNNING','LOCKED','SKIPPED')),
    queue_position      integer NOT NULL,
    game_local_number   integer NOT NULL,
    youtube_url         text    NULL,
    reference_video_url text    NULL,
    performance_score   numeric NULL,
    announced_at        timestamptz NULL,
    confirm_deadline_at timestamptz NULL,
    started_at          timestamptz NULL,
    locked_at           timestamptz NULL,
    version             bigint  NOT NULL DEFAULT 0,
    CONSTRAINT uq_performance_game_local_number UNIQUE (game_id, game_local_number)
);

CREATE INDEX idx_performance_game_queue ON performance (game_id, queue_position);
CREATE INDEX idx_performance_game_state ON performance (game_id, state);

-- performer_slot: one row per performer position (0..3) per performance
CREATE TABLE performer_slot (
    id                 uuid    PRIMARY KEY,
    game_id            uuid    NOT NULL REFERENCES game(id),
    performance_id     bigint  NOT NULL REFERENCES performance(id),
    slot_index         integer NOT NULL CHECK (slot_index BETWEEN 0 AND 3),
    origin             text    NOT NULL CHECK (origin IN ('PREDETERMINED','RANDOM')),
    original_player_id uuid    NULL REFERENCES player(id),
    current_player_id  uuid    NULL REFERENCES player(id),
    state              text    NOT NULL DEFAULT 'PENDING'
                               CHECK (state IN ('PENDING','CONFIRMED','REPLACED','VACATED')),
    confirmed_at       timestamptz NULL,
    CONSTRAINT uq_performer_slot_position UNIQUE (performance_id, slot_index)
);

CREATE UNIQUE INDEX uq_performer_slot_current_player
    ON performer_slot (performance_id, current_player_id)
    WHERE current_player_id IS NOT NULL;

-- judge_assignment: one row per judge per performance
CREATE TABLE judge_assignment (
    id               uuid    PRIMARY KEY,
    game_id          uuid    NOT NULL REFERENCES game(id),
    performance_id   bigint  NOT NULL REFERENCES performance(id),
    judge_player_id  uuid    NOT NULL REFERENCES player(id),
    source           text    NOT NULL CHECK (source IN ('ASSIGNED','AUTHOR')),
    CONSTRAINT uq_judge_assignment UNIQUE (performance_id, judge_player_id)
);
