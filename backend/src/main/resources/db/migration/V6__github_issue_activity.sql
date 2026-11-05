-- Adds optional GitHub repository mapping to projects and stores signed webhook activity.
-- Existing projects are left unlinked and no issue workflow fields are changed.
ALTER TABLE project
    ADD COLUMN github_repository_owner VARCHAR(39) NULL,
    ADD COLUMN github_repository_name VARCHAR(100) NULL,
    ADD CONSTRAINT uk_project_github_repository UNIQUE (github_repository_owner, github_repository_name);

CREATE TABLE github_webhook_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    repository_owner VARCHAR(39) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_github_webhook_delivery PRIMARY KEY (id),
    CONSTRAINT uk_github_webhook_delivery_id UNIQUE (delivery_id)
);

CREATE TABLE github_issue_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    activity_type VARCHAR(24) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    summary TEXT NOT NULL,
    html_url VARCHAR(2048) NOT NULL,
    author VARCHAR(255) NULL,
    event_at DATETIME(6) NULL,
    CONSTRAINT pk_github_issue_activity PRIMARY KEY (id),
    CONSTRAINT uk_github_activity_delivery_object_issue UNIQUE (delivery_id, external_id, issue_id),
    CONSTRAINT fk_github_activity_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE,
    CONSTRAINT fk_github_activity_delivery FOREIGN KEY (delivery_id) REFERENCES github_webhook_delivery (id) ON DELETE CASCADE,
    INDEX ix_github_activity_issue_event (issue_id, event_at, id)
);
