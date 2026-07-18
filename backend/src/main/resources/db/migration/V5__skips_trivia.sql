-- player_skip: a player's opt-out of random assignment for a specific performance
CREATE TABLE player_skip (
    performance_id  bigint      NOT NULL REFERENCES performance(id),
    player_id       uuid        NOT NULL REFERENCES player(id),
    game_id         uuid        NOT NULL REFERENCES game(id),
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (performance_id, player_id)
);

-- trivia_question: the 10 question+answer pairs submitted by the performance author
-- Answers visible to judges only (enforced at the API/real-time layer, not here)
CREATE TABLE trivia_question (
    id              uuid    PRIMARY KEY,
    performance_id  bigint  NOT NULL REFERENCES performance(id),
    game_id         uuid    NOT NULL REFERENCES game(id),
    ordinal         integer NOT NULL CHECK (ordinal BETWEEN 1 AND 10),
    question        text    NOT NULL,
    answer          text    NOT NULL,
    CONSTRAINT uq_trivia_question_ordinal UNIQUE (performance_id, ordinal)
);
