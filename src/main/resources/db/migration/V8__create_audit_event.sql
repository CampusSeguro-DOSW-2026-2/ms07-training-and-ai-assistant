CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(36)
);

CREATE INDEX idx_audit_event_user_id
    ON audit_event (user_id);

CREATE INDEX idx_audit_event_occurred_at
    ON audit_event (occurred_at);
