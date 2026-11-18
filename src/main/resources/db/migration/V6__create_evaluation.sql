-- RF-14: short evaluations of a category. Questions and options are replaced as a whole when the evaluation is updated.
CREATE TABLE evaluation (
    id          UUID PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    topic       VARCHAR(150) NOT NULL,
    category_id UUID         NOT NULL REFERENCES category (id),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_evaluation_category ON evaluation (category_id);

CREATE TABLE question (
    id            UUID PRIMARY KEY,
    evaluation_id UUID         NOT NULL REFERENCES evaluation (id) ON DELETE CASCADE,
    position      INTEGER      NOT NULL,
    statement     VARCHAR(500) NOT NULL
);

CREATE INDEX ix_question_evaluation ON question (evaluation_id);

CREATE TABLE question_option (
    id          UUID PRIMARY KEY,
    question_id UUID         NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL,
    text        VARCHAR(300) NOT NULL,
    is_correct  BOOLEAN      NOT NULL
);

CREATE INDEX ix_question_option_question ON question_option (question_id);
