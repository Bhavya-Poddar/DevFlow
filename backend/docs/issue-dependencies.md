# Issue dependencies and blockers

## Relationship meaning

`POST /api/issues/{blockedIssueId}/dependencies` creates a directed blocker relationship. The request names the blocking issue using `blockingIssueId` and must include a short human explanation in `reason`. The current issue stays in its existing workflow status; dependency links never transition or resolve an issue automatically.

`GET /api/issues` and `GET /api/issues/{id}` include `blocked`, `blockedBy`, and `blocks`. Each related-issue summary includes its current status from the issue record. An issue is considered blocked while at least one issue in `blockedBy` is not `RESOLVED`. The relationship remains visible after the blocker resolves, with its live status and reason, but `blocked` becomes false.

`DELETE /api/issues/{blockedIssueId}/dependencies/{blockingIssueId}` removes a relationship. ADMIN and PROJECT_MANAGER can add or remove relationships; other roles can read them through the normal issue read endpoints.

## Migration

Production profile migration `V4__issue_dependencies.sql` creates a new empty `issue_dependency` table, a unique constraint preventing duplicate pairs, foreign keys to `issue` and `user`, and a reverse lookup index. It does not update issue or user rows. Local development uses the existing Hibernate `update` profile to create the mapped table. Tests use the isolated H2 `create-drop` database.

No MySQL connection or migration was run while implementing this feature. Before production rollout, continue following `database-schema-management.md`: inspect the current schema and Flyway history, rehearse migrations on a clone, and verify the deployed schema before starting the application with the `prod` profile. This V4 migration assumes the existing `issue(id)` and `user(id)` tables use BIGINT primary keys, consistent with the entities and earlier migrations; confirm this against the target database before applying it.

### V4 read-only preflight

Before applying V4 to an existing MySQL database, take a verified backup and run these checks with a read-only account. Do not run the migration if the schema is unclear.

```sql
SELECT DATABASE(), VERSION();

SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('issue', 'user', 'issue_dependency');

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'issue' AND COLUMN_NAME = 'id')
    OR (TABLE_NAME = 'user' AND COLUMN_NAME = 'id'));

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, COLUMN_NAME, SEQ_IN_INDEX
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'issue_dependency'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'issue_dependency';

```

Run the final query only if `flyway_schema_history` exists; otherwise first classify the database as a legacy schema without Flyway history using the procedure in `database-schema-management.md`. If it exists:

```sql
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

If `issue_dependency` exists, inspect it with `SHOW CREATE TABLE issue_dependency` and inspect its row count and contents before proceeding. This can happen when the local `dev` profile previously let Hibernate create the table. V4 uses plain `CREATE TABLE` and will fail if the table already exists; do not work around that with `IF NOT EXISTS`, and do not baseline over an unreviewed table. Compare the table's columns, primary key, unique pair constraint, foreign keys, and reverse lookup index with V4 and the `IssueDependency` entity. If the existing table is equivalent, an operator must deliberately reconcile it with Flyway history so V4 is not run a second time; if it is partial or different, stop for a reviewed reconciliation migration.

Confirm `issue` and `user` are InnoDB tables and that their `id` columns are both signed `BIGINT` (including matching nullability/key definitions). MySQL foreign keys require compatible referenced and referencing types and a foreign-key-capable engine. Also confirm the actual table names match the entity mappings (`Issue` maps to `issue`; `User` currently relies on the naming strategy mapping `User` to the reserved-word table name `user`, which V4 quotes). Do not infer these details from H2 tests.

V4 deliberately defines no `ON DELETE CASCADE`. Normal issue deletion removes dependency rows in the same service transaction before deleting the issue; direct database deletion is expected to be restricted by the foreign keys. The unique pair constraint prevents duplicate links at the database layer. The migration creates an empty relationship table and does not backfill links or change issue/user rows.
