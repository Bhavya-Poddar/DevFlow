-- Stores one DevFlow task's outbound GitHub issue creation/link state.
-- CREATING/UNKNOWN records prevent unsafe automatic retries after ambiguous HTTP outcomes.
CREATE TABLE github_issue_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    repository_owner VARCHAR(39) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    github_issue_number BIGINT NULL,
    html_url VARCHAR(2048) NULL,
    title VARCHAR(512) NULL,
    state VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_github_issue_link PRIMARY KEY (id),
    CONSTRAINT uk_github_issue_link_task UNIQUE (issue_id),
    CONSTRAINT uk_github_issue_link_repo_number UNIQUE (repository_owner, repository_name, github_issue_number),
    CONSTRAINT fk_github_issue_link_task FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE,
    INDEX ix_github_issue_link_repository (repository_owner, repository_name, state)
);
