-- evaluation: a judge's structured evaluation (one per judge per performance)
CREATE TABLE evaluation (
    id               uuid        PRIMARY KEY,
    game_id          uuid        NOT NULL REFERENCES game(id),
    performance_id   bigint      NOT NULL REFERENCES performance(id),
    judge_player_id  uuid        NOT NULL REFERENCES player(id),
    submitted_at     timestamptz NULL,
    CONSTRAINT uq_evaluation UNIQUE (performance_id, judge_player_id)
);

-- evaluation_score: per-criterion scores within an evaluation
-- A row with subject_player_id IS NULL = the whole-group baseline score
CREATE TABLE evaluation_score (
    id                uuid     PRIMARY KEY,
    evaluation_id     uuid     NOT NULL REFERENCES evaluation(id),
    subject_player_id uuid     NULL REFERENCES player(id),
    criterion         text     NOT NULL,
    value             numeric  NOT NULL,
    CONSTRAINT uq_evaluation_score UNIQUE (evaluation_id, subject_player_id, criterion)
);

-- rating: one per audience player per performance; composite PK enforces uniqueness
CREATE TABLE rating (
    performance_id  bigint      NOT NULL REFERENCES performance(id),
    player_id       uuid        NOT NULL REFERENCES player(id),
    game_id         uuid        NOT NULL REFERENCES game(id),
    total_score     numeric     NOT NULL DEFAULT 0,
    locked          boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,  -- optimistic locking
    PRIMARY KEY (performance_id, player_id)
);

-- rating_score: per-criterion sub-scores behind a rating's total_score
CREATE TABLE rating_score (
    performance_id  bigint   NOT NULL,
    player_id       uuid     NOT NULL,
    criterion       text     NOT NULL,
    value           numeric  NOT NULL,
    PRIMARY KEY (performance_id, player_id, criterion),
    FOREIGN KEY (performance_id, player_id) REFERENCES rating (performance_id, player_id)
);
