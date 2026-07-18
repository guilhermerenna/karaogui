-- performance_type enum
CREATE TYPE performance_type AS ENUM (
    'KARAOKE', 'MAGIC_TRICK', 'DANCE', 'ACTING_MIMIC',
    'STANDUP', 'TRIVIA', 'REVERSE_MIMIC', 'PHYSICAL_CHALLENGE'
);

-- physical_challenge_kind enum (cyclic order: BALLOON→WATER→MARSHMALLOW→COOKIE→…)
CREATE TYPE physical_challenge_kind AS ENUM (
    'BALLOON', 'WATER', 'MARSHMALLOW', 'COOKIE'
);

-- performance_state enum
CREATE TYPE performance_state AS ENUM (
    'QUEUED', 'ANNOUNCED', 'CONFIRMING', 'RUNNING', 'LOCKED', 'SKIPPED'
);

-- performer_slot_state enum
CREATE TYPE performer_slot_state AS ENUM (
    'PENDING', 'CONFIRMED', 'REPLACED', 'VACATED'
);

-- judge_role_source enum
CREATE TYPE judge_role_source AS ENUM ('ASSIGNED', 'AUTHOR');

-- performance: a single scored act; PK is a global bigint; user-visible number is game_local_number
CREATE TABLE performance (
    id                  bigint              PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    game_id             uuid                NOT NULL REFERENCES game(id),
    type                performance_type    NOT NULL,
    physical_kind       physical_challenge_kind NULL,  -- non-null iff type=PHYSICAL_CHALLENGE
    author_player_id    uuid                NULL REFERENCES player(id),
    state               performance_state   NOT NULL DEFAULT 'QUEUED',
    queue_position      integer             NOT NULL,
    game_local_number   integer             NOT NULL,  -- user-visible, unique within game
    youtube_url         text                NULL,
    reference_video_url text                NULL,
    performance_score   numeric             NULL,
    announced_at        timestamptz         NULL,
    confirm_deadline_at timestamptz         NULL,
    started_at          timestamptz         NULL,
    locked_at           timestamptz         NULL,
    version             bigint              NOT NULL DEFAULT 0,  -- optimistic locking
    CONSTRAINT uq_performance_game_local_number UNIQUE (game_id, game_local_number)
);

CREATE INDEX idx_performance_game_queue ON performance (game_id, queue_position);
CREATE INDEX idx_performance_game_state ON performance (game_id, state);

-- performer_slot: one row per performer position (0..3) per performance
CREATE TABLE performer_slot (
    id                 uuid                 PRIMARY KEY,
    game_id            uuid                 NOT NULL REFERENCES game(id),
    performance_id     bigint               NOT NULL REFERENCES performance(id),
    slot_index         integer              NOT NULL CHECK (slot_index BETWEEN 0 AND 3),
    origin             text                 NOT NULL CHECK (origin IN ('PREDETERMINED', 'RANDOM')),
    original_player_id uuid                 NULL REFERENCES player(id),
    current_player_id  uuid                 NULL REFERENCES player(id),
    state              performer_slot_state NOT NULL DEFAULT 'PENDING',
    confirmed_at       timestamptz          NULL,
    CONSTRAINT uq_performer_slot_position    UNIQUE (performance_id, slot_index)
);

-- partial unique: a player can occupy at most one active slot per performance
CREATE UNIQUE INDEX uq_performer_slot_current_player
    ON performer_slot (performance_id, current_player_id)
    WHERE current_player_id IS NOT NULL;

-- judge_assignment: one row per judge per performance
CREATE TABLE judge_assignment (
    id               uuid               PRIMARY KEY,
    game_id          uuid               NOT NULL REFERENCES game(id),
    performance_id   bigint             NOT NULL REFERENCES performance(id),
    judge_player_id  uuid               NOT NULL REFERENCES player(id),
    source           judge_role_source  NOT NULL,
    CONSTRAINT uq_judge_assignment UNIQUE (performance_id, judge_player_id)
);
