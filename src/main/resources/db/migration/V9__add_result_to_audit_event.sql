-- RF-15 step 8: a tutor query records whether it was answered or out of scope. Nullable for other events.
ALTER TABLE audit_event ADD COLUMN result VARCHAR(20);
