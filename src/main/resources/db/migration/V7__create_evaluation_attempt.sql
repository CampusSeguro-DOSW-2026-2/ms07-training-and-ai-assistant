-- RF-14: each time a user answers an evaluation. The topic is copied so the result keeps the topic it was taken with.
CREATE TABLE evaluation_attempt (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL,
    evaluation_id UUID         NOT NULL REFERENCES evaluation (id),
    topic         VARCHAR(150) NOT NULL,
    score         INTEGER      NOT NULL CHECK (score BETWEEN 0 AND 100),
    attempted_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_evaluation_attempt_user ON evaluation_attempt (user_id);
CREATE INDEX ix_evaluation_attempt_evaluation ON evaluation_attempt (evaluation_id);
