-- Project-team uniqueness prevents a team from being shared across projects.
-- Existing duplicate team links intentionally cause this migration to fail for DBA review;
-- no project or team is reassigned or deleted automatically.
ALTER TABLE project ADD CONSTRAINT uk_project_team UNIQUE (team_id);

ALTER TABLE issue ADD COLUMN workflow_cycle INT NOT NULL DEFAULT 1;
ALTER TABLE test_execution ADD COLUMN workflow_cycle INT NOT NULL DEFAULT 1;

CREATE TABLE issue_approval (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    workflow_cycle INT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    remark TEXT NULL,
    actor_id BIGINT NOT NULL,
    decided_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_issue_approval PRIMARY KEY (id),
    CONSTRAINT uk_issue_approval_cycle_stage UNIQUE (issue_id, workflow_cycle, stage),
    CONSTRAINT ck_issue_approval_stage CHECK (stage IN ('TEAM_LEAD', 'PROJECT_MANAGER')),
    CONSTRAINT ck_issue_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT fk_issue_approval_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE RESTRICT,
    CONSTRAINT fk_issue_approval_actor FOREIGN KEY (actor_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    INDEX ix_issue_approval_actor_time (actor_id, decided_at)
) ENGINE=InnoDB;

CREATE TABLE issue_maintenance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_issue_maintenance PRIMARY KEY (id),
    CONSTRAINT uk_issue_maintenance_issue UNIQUE (issue_id),
    CONSTRAINT ck_issue_maintenance_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT fk_issue_maintenance_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE RESTRICT,
    INDEX ix_issue_maintenance_expiry (status, expires_at)
) ENGINE=InnoDB;

CREATE TABLE issue_maintenance_follow_up (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_issue_id BIGINT NOT NULL,
    follow_up_issue_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    responsible_user_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    reported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_issue_maintenance_follow_up PRIMARY KEY (id),
    CONSTRAINT uk_maintenance_followup_issue UNIQUE (follow_up_issue_id),
    CONSTRAINT fk_maintenance_followup_original FOREIGN KEY (original_issue_id) REFERENCES issue (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_followup_issue FOREIGN KEY (follow_up_issue_id) REFERENCES issue (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_followup_reporter FOREIGN KEY (reporter_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_followup_responsible FOREIGN KEY (responsible_user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    INDEX ix_maintenance_followup_original (original_issue_id, reported_at)
) ENGINE=InnoDB;

INSERT IGNORE INTO permission_catalogue (permission_key, description, scope_type) VALUES
('ISSUE_APPROVE_TEAM_LEAD', 'Approve or reject tested tasks at Team Lead review', 'PROJECT'),
('ISSUE_APPROVE_PROJECT_MANAGER', 'Approve or reject Team Lead-approved tasks', 'PROJECT');

INSERT IGNORE INTO role_definition (code, name, scope_type, retired, system_role, description)
VALUES ('TEAM_LEAD_PROJECT', 'Team Lead (Project)', 'PROJECT', FALSE, TRUE, 'Project-scoped Team Lead review access');

INSERT IGNORE INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r CROSS JOIN permission_catalogue p
WHERE r.code = 'TEAM_LEAD_PROJECT' AND p.permission_key IN ('PROJECT_READ','ISSUE_READ','ISSUE_APPROVE_TEAM_LEAD','COMMENT_READ');
INSERT IGNORE INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r CROSS JOIN permission_catalogue p
WHERE r.code = 'PROJECT_MANAGER_PROJECT' AND p.permission_key = 'ISSUE_APPROVE_PROJECT_MANAGER';

INSERT IGNORE INTO admin_website_setting (setting_key, setting_value)
VALUES ('maintenance.period.days', '30');

INSERT IGNORE INTO admin_dropdown_option (category, code, display_label, enabled, sort_order) VALUES
('ISSUE_STATUS', 'TEAM_LEAD_APPROVAL', 'Team Lead approval', TRUE, 55),
('ISSUE_STATUS', 'PROJECT_MANAGER_APPROVAL', 'Project Manager approval', TRUE, 58);
