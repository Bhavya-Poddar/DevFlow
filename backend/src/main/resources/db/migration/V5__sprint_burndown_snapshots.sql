-- Stores one real remaining-issue observation per active sprint and UTC date.
-- Existing issue, sprint, user, and audit rows are not modified.
CREATE TABLE sprint_burndown_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sprint_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    total_issues BIGINT NOT NULL,
    remaining_issues BIGINT NOT NULL,
    CONSTRAINT pk_sprint_burndown_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_sprint_burndown_date UNIQUE (sprint_id, snapshot_date),
    CONSTRAINT fk_sprint_burndown_sprint FOREIGN KEY (sprint_id) REFERENCES sprint (id) ON DELETE RESTRICT
);
