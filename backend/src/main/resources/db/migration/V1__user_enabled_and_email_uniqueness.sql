-- Draft incremental migration for a pre-existing DevFlow schema.
-- Apply only after the documented schema and data preflight has passed.
-- This deliberately does not normalize, delete, merge, or otherwise rewrite users.
ALTER TABLE `user`
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT uk_user_email UNIQUE (email);
