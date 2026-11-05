-- Durable workflow event outbox, configurable notification rules, and execution history.
-- Events intentionally keep a numeric issue reference without a foreign key so task deletion
-- cannot erase or block processing/audit history. No historical events are created here.
CREATE TABLE automation_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    trigger_event VARCHAR(40) NOT NULL,
    condition_status VARCHAR(40) NULL,
    action_type VARCHAR(40) NOT NULL,
    built_in_key VARCHAR(80) NULL,
    created_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_automation_rule PRIMARY KEY (id),
    CONSTRAINT uk_automation_rule_builtin_key UNIQUE (built_in_key),
    CONSTRAINT fk_automation_rule_creator FOREIGN KEY (created_by_user_id)
        REFERENCES `user` (id) ON DELETE SET NULL,
    INDEX ix_automation_rule_trigger_enabled (trigger_event, enabled)
);

CREATE TABLE automation_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_key VARCHAR(160) NOT NULL,
    trigger_event VARCHAR(40) NOT NULL,
    issue_id BIGINT NULL,
    previous_status VARCHAR(40) NULL,
    resulting_status VARCHAR(40) NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    error_summary VARCHAR(300) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    CONSTRAINT pk_automation_event PRIMARY KEY (id),
    CONSTRAINT uk_automation_event_source_key UNIQUE (source_key),
    INDEX ix_automation_event_pending (state, next_attempt_at, created_at)
);

CREATE TABLE automation_execution (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    rule_id BIGINT NULL,
    rule_name VARCHAR(120) NOT NULL,
    trigger_event VARCHAR(40) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    target_issue_id BIGINT NULL,
    outcome VARCHAR(20) NOT NULL,
    summary VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_automation_execution PRIMARY KEY (id),
    CONSTRAINT uk_automation_execution_event_rule UNIQUE (event_id, rule_id),
    CONSTRAINT fk_automation_execution_event FOREIGN KEY (event_id)
        REFERENCES automation_event (id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_execution_rule FOREIGN KEY (rule_id)
        REFERENCES automation_rule (id) ON DELETE SET NULL,
    INDEX ix_automation_execution_recent (created_at, id)
);
