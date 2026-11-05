-- Preserve deleted built-in automation keys so startup initialization does not recreate them.
ALTER TABLE automation_rule
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0;
