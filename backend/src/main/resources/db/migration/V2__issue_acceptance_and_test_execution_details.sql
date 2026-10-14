-- Adds issue acceptance criteria and structured details to existing test executions.
-- This is additive and does not update or delete existing issue or test-history data.
ALTER TABLE issue
    ADD COLUMN acceptance_criteria TEXT NULL;

ALTER TABLE test_execution
    ADD COLUMN test_scenario TEXT NULL,
    ADD COLUMN expected_result TEXT NULL,
    ADD COLUMN actual_result TEXT NULL,
    MODIFY COLUMN notes TEXT NULL;

ALTER TABLE audit_log
    MODIFY COLUMN details TEXT NULL;
