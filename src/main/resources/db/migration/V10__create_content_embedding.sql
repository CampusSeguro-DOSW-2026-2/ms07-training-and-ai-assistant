-- RF-15: embedding of each content (title and text) for the search by meaning of the tutor.
-- The vector is a JSON array in TEXT so PostgreSQL does not need pgvector; with a few dozen contents the
-- similarity is computed in MS07. content_updated_at says which version of the content it belongs to.
CREATE TABLE content_embedding (
    content_id         UUID PRIMARY KEY REFERENCES content (id) ON DELETE CASCADE,
    model              VARCHAR(100) NOT NULL,
    vector             TEXT         NOT NULL,
    content_updated_at TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
