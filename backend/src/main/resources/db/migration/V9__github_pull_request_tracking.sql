-- Stores the latest supported GitHub pull-request state separately from the immutable activity feed.
CREATE TABLE github_pull_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    repository_owner VARCHAR(39) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    pull_request_number BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    html_url VARCHAR(2048) NOT NULL,
    author VARCHAR(255) NULL,
    head_branch VARCHAR(255) NULL,
    base_branch VARCHAR(255) NULL,
    state VARCHAR(16) NOT NULL,
    merged BOOLEAN NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,
    merged_at DATETIME(6) NULL,
    CONSTRAINT pk_github_pull_request PRIMARY KEY (id),
    CONSTRAINT uk_github_pull_request_repository_number UNIQUE (repository_owner, repository_name, pull_request_number),
    CONSTRAINT fk_github_pr_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    INDEX ix_github_pr_project_updated (project_id, updated_at, id)
);

CREATE TABLE github_pull_request_issue (
    pull_request_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    CONSTRAINT pk_github_pull_request_issue PRIMARY KEY (pull_request_id, issue_id),
    CONSTRAINT fk_github_pr_issue_pr FOREIGN KEY (pull_request_id) REFERENCES github_pull_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_github_pr_issue_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE,
    INDEX ix_github_pr_issue_issue (issue_id, pull_request_id)
);
