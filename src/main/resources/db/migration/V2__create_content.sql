-- RF-10: learning contents, each one in a category.
CREATE TABLE content (
    id          UUID PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    body        VARCHAR(20000) NOT NULL,
    category_id UUID         NOT NULL REFERENCES category (id),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_content_category ON content (category_id);
