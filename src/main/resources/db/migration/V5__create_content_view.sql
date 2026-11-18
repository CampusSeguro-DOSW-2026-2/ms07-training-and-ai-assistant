-- RF-13: contents each user has opened. One row per user and content: a new view only moves last_viewed_at.
CREATE TABLE content_view (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL,
    content_id      UUID        NOT NULL REFERENCES content (id),
    first_viewed_at TIMESTAMPTZ NOT NULL,
    last_viewed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_content_view_user_content UNIQUE (user_id, content_id)
);

CREATE INDEX ix_content_view_content ON content_view (content_id);
