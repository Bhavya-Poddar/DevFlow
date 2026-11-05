ALTER TABLE role_definition ADD COLUMN description VARCHAR(250) NULL;

CREATE TABLE admin_dropdown_option (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(40) NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_label VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_admin_dropdown_option PRIMARY KEY (id),
    CONSTRAINT uk_admin_dropdown_option UNIQUE (category, code),
    INDEX ix_admin_dropdown_category_order (category, enabled, sort_order)
) ENGINE=InnoDB;

CREATE TABLE admin_menu_entry (
    route_key VARCHAR(64) NOT NULL,
    display_label VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_admin_menu_entry PRIMARY KEY (route_key)
) ENGINE=InnoDB;

CREATE TABLE admin_website_setting (
    setting_key VARCHAR(80) NOT NULL,
    setting_value VARCHAR(240) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_admin_website_setting PRIMARY KEY (setting_key)
) ENGINE=InnoDB;

INSERT INTO admin_dropdown_option (category, code, display_label, enabled, sort_order) VALUES
('PRIORITY', 'LOW', 'Low', TRUE, 10),
('PRIORITY', 'MEDIUM', 'Medium', TRUE, 20),
('PRIORITY', 'HIGH', 'High', TRUE, 30),
('PRIORITY', 'CRITICAL', 'Critical', TRUE, 40);

INSERT INTO admin_menu_entry (route_key, display_label, enabled, sort_order) VALUES
('dashboard', 'Dashboard', TRUE, 10), ('users', 'Users', TRUE, 20),
('projects', 'Projects', TRUE, 30), ('private-teams', 'Private Teams', TRUE, 40),
('tasks', 'Tasks', TRUE, 50), ('sprints', 'Sprints', TRUE, 60),
('testing', 'Testing', TRUE, 70), ('my-tests', 'My Tests', TRUE, 80),
('code-reviews', 'Code Review', TRUE, 90), ('comments', 'Comments', TRUE, 100),
('reports', 'Reports', TRUE, 110), ('activity', 'Activity', TRUE, 120),
('audit-logs', 'Audit Logs', TRUE, 130), ('notifications', 'Notifications', TRUE, 140),
('automations', 'Automations', TRUE, 150), ('settings', 'Settings', TRUE, 160);

INSERT INTO admin_website_setting (setting_key, setting_value) VALUES
('workspace.name', 'DevFlow'), ('workspace.tagline', 'Engineering OS');
