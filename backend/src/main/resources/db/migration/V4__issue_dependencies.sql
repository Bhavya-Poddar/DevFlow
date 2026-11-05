-- Adds explicit issue blocker relationships. Existing issue records are untouched.
CREATE TABLE issue_dependency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocked_issue_id BIGINT NOT NULL,
    blocking_issue_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_issue_dependency PRIMARY KEY (id),
    CONSTRAINT uk_issue_dependency_pair UNIQUE (blocked_issue_id, blocking_issue_id),
    CONSTRAINT fk_issue_dependency_blocked FOREIGN KEY (blocked_issue_id) REFERENCES issue (id),
    CONSTRAINT fk_issue_dependency_blocker FOREIGN KEY (blocking_issue_id) REFERENCES issue (id),
    CONSTRAINT fk_issue_dependency_creator FOREIGN KEY (created_by_user_id) REFERENCES `user` (id),
    INDEX ix_issue_dependency_blocker (blocking_issue_id)
);
