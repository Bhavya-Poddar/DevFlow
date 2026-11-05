-- Database-backed roles and permission grants. Existing user.role values remain stable role codes.
-- Project/team assignments are backfilled only where the current access model already grants visibility.
ALTER TABLE `user`
    ADD COLUMN admin_recovery BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE `user`
SET admin_recovery = TRUE
WHERE enabled = TRUE AND UPPER(role) = 'ADMIN';

CREATE TABLE permission_catalogue (
    permission_key VARCHAR(80) NOT NULL,
    description VARCHAR(180) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    CONSTRAINT pk_permission_catalogue PRIMARY KEY (permission_key),
    CONSTRAINT ck_permission_scope CHECK (scope_type IN ('GLOBAL', 'PROJECT', 'PROJECT_TEAM'))
) ENGINE=InnoDB;

CREATE TABLE role_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    retired BOOLEAN NOT NULL DEFAULT FALSE,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_role_definition PRIMARY KEY (id),
    CONSTRAINT uk_role_code UNIQUE (code),
    CONSTRAINT ck_role_scope CHECK (scope_type IN ('GLOBAL', 'PROJECT', 'PROJECT_TEAM'))
) ENGINE=InnoDB;

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_key VARCHAR(80) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_key),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permission_catalogue FOREIGN KEY (permission_key) REFERENCES permission_catalogue (permission_key) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE project_role_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_project_role_assignment PRIMARY KEY (id),
    CONSTRAINT uk_project_role_assignment UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_role_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_role_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_role_role FOREIGN KEY (role_id) REFERENCES role_definition (id) ON DELETE RESTRICT,
    INDEX ix_project_role_user (user_id, project_id),
    INDEX ix_project_role_role (role_id)
) ENGINE=InnoDB;

CREATE TABLE team_role_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_team_role_assignment PRIMARY KEY (id),
    CONSTRAINT uk_team_role_assignment UNIQUE (team_id, user_id),
    CONSTRAINT fk_team_role_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_role_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_role_role FOREIGN KEY (role_id) REFERENCES role_definition (id) ON DELETE RESTRICT,
    INDEX ix_team_role_user (user_id, team_id),
    INDEX ix_team_role_role (role_id)
) ENGINE=InnoDB;

CREATE TABLE authorization_invariant_lock (
    id INT NOT NULL,
    CONSTRAINT pk_authorization_invariant_lock PRIMARY KEY (id),
    CONSTRAINT ck_authorization_invariant_lock CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO authorization_invariant_lock (id) VALUES (1);

INSERT INTO permission_catalogue (permission_key, description, scope_type) VALUES
('USER_MANAGE', 'Create and manage user accounts', 'GLOBAL'),
('USER_READ', 'Read the administrative user directory', 'GLOBAL'),
('PROJECT_CREATE', 'Create projects', 'GLOBAL'),
('PROJECT_READ_ALL', 'Read all projects, including private projects', 'GLOBAL'),
('TEAM_CREATE', 'Create teams', 'GLOBAL'),
('TEAM_READ_ALL', 'Read all teams', 'GLOBAL'),
('AUDIT_DELETE', 'Delete audit records', 'GLOBAL'),
('AUDIT_READ_ALL', 'Read unscoped audit history', 'GLOBAL'),
('DASHBOARD_READ_ALL', 'Read unscoped dashboard metrics', 'GLOBAL'),
('TESTING_OVERRIDE', 'Override assigned-tester ownership checks', 'GLOBAL'),
('ASSIGNABLE_DEVELOPER', 'May be assigned as accountable developer', 'GLOBAL'),
('ASSIGNABLE_TESTER', 'May be assigned as tester', 'GLOBAL'),
('PROJECT_READ', 'Read projects', 'PROJECT'),
('PROJECT_UPDATE', 'Update project details', 'PROJECT'),
('PROJECT_DELETE', 'Delete projects', 'PROJECT'),
('PROJECT_ROLE_MANAGE', 'Assign roles to members of this project', 'PROJECT'),
('ISSUE_READ', 'Read tasks', 'PROJECT'),
('ISSUE_CREATE', 'Create tasks', 'PROJECT'),
('ISSUE_UPDATE', 'Update tasks within assigned ownership rules', 'PROJECT'),
('ISSUE_UPDATE_ALL', 'Update any task in this scope', 'PROJECT'),
('ISSUE_UPDATE_ASSIGNED', 'Update tasks assigned to the current user', 'PROJECT'),
('ISSUE_ASSIGN_DEVELOPER', 'Assign task developers', 'PROJECT'),
('ISSUE_STATUS_MANAGE', 'Set permitted manager-level task statuses', 'PROJECT'),
('ISSUE_STATUS_ADVANCE', 'Advance assigned tasks through developer states', 'PROJECT'),
('ISSUE_DELETE', 'Delete tasks', 'PROJECT'),
('TESTER_ASSIGN', 'Assign testers to tasks', 'PROJECT'),
('ISSUE_DEPENDENCY_MANAGE', 'Create and remove task dependencies', 'PROJECT'),
('GITHUB_ISSUE_CREATE', 'Create or reconcile linked GitHub issues', 'PROJECT'),
('COMMENT_READ', 'Read task comments', 'PROJECT'),
('COMMENT_CREATE', 'Create task comments', 'PROJECT'),
('COMMENT_DELETE', 'Delete comments', 'PROJECT'),
('REPORT_READ', 'Read project and sprint reports', 'PROJECT'),
('DASHBOARD_READ', 'Read project dashboard metrics', 'PROJECT'),
('AUDIT_READ', 'Read project audit history', 'PROJECT'),
('AUTOMATION_MANAGE', 'Manage project workflow automation', 'PROJECT'),
('TESTING_QUEUE_READ', 'Read the testing queue', 'PROJECT'),
('TESTING_MY_READ', 'Read assigned testing work', 'PROJECT'),
('TESTING_HISTORY_READ', 'Read task testing history', 'PROJECT'),
('TEST_EXECUTE', 'Start and complete assigned testing', 'PROJECT'),
('TEAM_READ', 'Read team details', 'PROJECT_TEAM'),
('TEAM_MANAGE', 'Manage team membership', 'PROJECT_TEAM'),
('TEAM_ACTIVITY_READ', 'Read team activity', 'PROJECT_TEAM'),
('TEAM_ROLE_MANAGE', 'Assign roles to existing team members', 'PROJECT_TEAM');

INSERT INTO role_definition (code, name, scope_type, retired, system_role) VALUES
('ADMIN', 'Administrator', 'GLOBAL', FALSE, TRUE),
('PROJECT_MANAGER', 'Project Manager', 'GLOBAL', FALSE, TRUE),
('DEVELOPER', 'Developer', 'GLOBAL', FALSE, TRUE),
('TESTER', 'Tester', 'GLOBAL', FALSE, TRUE),
('VIEWER', 'Viewer', 'GLOBAL', FALSE, TRUE),
('PROJECT_MANAGER_PROJECT', 'Project Manager (Project)', 'PROJECT', FALSE, TRUE),
('DEVELOPER_PROJECT', 'Developer (Project)', 'PROJECT', FALSE, TRUE),
('TESTER_PROJECT', 'Tester (Project)', 'PROJECT', FALSE, TRUE),
('VIEWER_PROJECT', 'Viewer (Project)', 'PROJECT', FALSE, TRUE),
('PROJECT_MANAGER_TEAM', 'Project Manager (Team)', 'PROJECT_TEAM', FALSE, TRUE),
('TEAM_LEAD_TEAM', 'Team Lead', 'PROJECT_TEAM', FALSE, TRUE),
('DEVELOPER_TEAM', 'Developer (Team)', 'PROJECT_TEAM', FALSE, TRUE),
('TESTER_TEAM', 'Tester (Team)', 'PROJECT_TEAM', FALSE, TRUE),
('VIEWER_TEAM', 'Viewer (Team)', 'PROJECT_TEAM', FALSE, TRUE);

-- Default grants are inserted only for the initial built-in roles. Runtime initialization never overwrites edits.
INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r CROSS JOIN permission_catalogue p WHERE r.code = 'ADMIN';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_CREATE','TEAM_CREATE','PROJECT_READ','PROJECT_UPDATE','PROJECT_DELETE','ISSUE_READ','ISSUE_CREATE',
 'ISSUE_UPDATE','ISSUE_UPDATE_ALL','ISSUE_ASSIGN_DEVELOPER','ISSUE_STATUS_MANAGE','ISSUE_DELETE','TESTER_ASSIGN',
 'ISSUE_DEPENDENCY_MANAGE','GITHUB_ISSUE_CREATE','COMMENT_READ','COMMENT_CREATE','COMMENT_DELETE','REPORT_READ',
 'DASHBOARD_READ','AUDIT_READ','AUTOMATION_MANAGE','TESTING_QUEUE_READ','TESTING_HISTORY_READ')
WHERE r.code = 'PROJECT_MANAGER';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','ISSUE_READ','ISSUE_CREATE','ISSUE_UPDATE','ISSUE_UPDATE_ASSIGNED','ISSUE_STATUS_ADVANCE',
 'COMMENT_READ','COMMENT_CREATE','REPORT_READ','DASHBOARD_READ','TESTING_HISTORY_READ')
WHERE r.code = 'DEVELOPER';
INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key = 'ASSIGNABLE_DEVELOPER'
WHERE r.code = 'DEVELOPER';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','ISSUE_READ','COMMENT_READ','REPORT_READ','DASHBOARD_READ','TESTING_HISTORY_READ','TESTING_MY_READ',
 'TESTING_QUEUE_READ','TEST_EXECUTE')
WHERE r.code = 'TESTER';
INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key = 'ASSIGNABLE_TESTER'
WHERE r.code = 'TESTER';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','ISSUE_READ','COMMENT_READ','REPORT_READ','DASHBOARD_READ','TESTING_HISTORY_READ')
WHERE r.code = 'VIEWER';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','PROJECT_UPDATE','PROJECT_DELETE','PROJECT_ROLE_MANAGE','ISSUE_READ','ISSUE_CREATE','ISSUE_UPDATE',
 'ISSUE_UPDATE_ALL','ISSUE_ASSIGN_DEVELOPER','ISSUE_STATUS_MANAGE','ISSUE_DELETE',
 'TESTER_ASSIGN','ISSUE_DEPENDENCY_MANAGE','GITHUB_ISSUE_CREATE','COMMENT_READ','COMMENT_CREATE','COMMENT_DELETE',
 'REPORT_READ','DASHBOARD_READ','AUDIT_READ','AUTOMATION_MANAGE','TESTING_QUEUE_READ','TESTING_HISTORY_READ')
WHERE r.code = 'PROJECT_MANAGER_PROJECT';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','ISSUE_READ','ISSUE_CREATE','ISSUE_UPDATE','ISSUE_UPDATE_ASSIGNED','ISSUE_STATUS_ADVANCE',
 'COMMENT_READ','COMMENT_CREATE','REPORT_READ',
 'DASHBOARD_READ','TESTING_HISTORY_READ') WHERE r.code = 'DEVELOPER_PROJECT';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','ISSUE_READ','COMMENT_READ','REPORT_READ','DASHBOARD_READ','TESTING_QUEUE_READ','TESTING_MY_READ',
 'TESTING_HISTORY_READ','TEST_EXECUTE') WHERE r.code = 'TESTER_PROJECT';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('PROJECT_READ','ISSUE_READ','COMMENT_READ','REPORT_READ','DASHBOARD_READ','TESTING_HISTORY_READ')
WHERE r.code = 'VIEWER_PROJECT';

INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('TEAM_READ','TEAM_MANAGE','TEAM_ACTIVITY_READ','TEAM_ROLE_MANAGE') WHERE r.code = 'PROJECT_MANAGER_TEAM';
INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN
('TEAM_READ','TEAM_ACTIVITY_READ') WHERE r.code = 'TEAM_LEAD_TEAM';
INSERT INTO role_permission (role_id, permission_key)
SELECT r.id, p.permission_key FROM role_definition r JOIN permission_catalogue p ON p.permission_key IN ('TEAM_READ','TEAM_ACTIVITY_READ')
WHERE r.code IN ('DEVELOPER_TEAM','TESTER_TEAM','VIEWER_TEAM');

-- Preserve current project permissions only in projects users could already see under V12.
INSERT INTO project_role_assignment (project_id, user_id, role_id)
SELECT p.id, u.id, r.id
FROM project p JOIN `user` u ON u.enabled = TRUE
JOIN role_definition r ON r.code = CONCAT(UPPER(u.role), '_PROJECT')
WHERE UPPER(u.role) IN ('PROJECT_MANAGER','DEVELOPER','TESTER','VIEWER')
  AND (p.team_id IS NULL OR EXISTS (
      SELECT 1 FROM team_member tm WHERE tm.team_id = p.team_id AND tm.user_id = u.id
  ) OR EXISTS (
      SELECT 1 FROM team t WHERE t.id = p.team_id AND t.manager_id = u.id
  ));

-- Preserve current team membership and assign the matching scoped capability. This does not create memberships.
INSERT INTO team_role_assignment (team_id, user_id, role_id)
SELECT tm.team_id, tm.user_id, r.id
FROM team_member tm JOIN `user` u ON u.id = tm.user_id AND u.enabled = TRUE
JOIN role_definition r ON r.code = CONCAT(UPPER(u.role), '_TEAM')
WHERE UPPER(u.role) IN ('PROJECT_MANAGER','DEVELOPER','TESTER','VIEWER');

INSERT INTO team_role_assignment (team_id, user_id, role_id)
SELECT t.id, t.manager_id, r.id
FROM team t JOIN `user` u ON u.id = t.manager_id AND u.enabled = TRUE
JOIN role_definition r ON r.code = CONCAT(UPPER(u.role), '_TEAM')
WHERE UPPER(u.role) IN ('PROJECT_MANAGER','DEVELOPER','TESTER','VIEWER')
  AND NOT EXISTS (SELECT 1 FROM team_role_assignment a WHERE a.team_id = t.id AND a.user_id = t.manager_id);
