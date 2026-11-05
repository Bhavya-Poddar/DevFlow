-- Manager-led teams. Existing projects remain unassigned and retain their current shared visibility.
CREATE TABLE team (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    manager_id BIGINT NOT NULL,
    CONSTRAINT pk_team PRIMARY KEY (id),
    CONSTRAINT uk_team_name UNIQUE (name),
    CONSTRAINT fk_team_manager FOREIGN KEY (manager_id) REFERENCES `user` (id) ON DELETE RESTRICT
);

CREATE TABLE team_member (
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_team_member PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    INDEX ix_team_member_user (user_id, team_id)
);

ALTER TABLE project
    ADD COLUMN team_id BIGINT NULL,
    ADD CONSTRAINT fk_project_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    ADD INDEX ix_project_team (team_id);
