# DevFlow database schema management

## Configuration by environment

| Environment | Selection | Hibernate | Flyway | Database |
| --- | --- | --- | --- | --- |
| Safe default | No profile | `validate` | Disabled | Environment-configured; no schema writes |
| Local development | `SPRING_PROFILES_ACTIVE=dev` plus `DEVFLOW_DEV_DB_CONFIRMATION=LOCAL_DATABASE_ONLY` | `update` | Disabled | Loopback MySQL only |
| Tests | Test resources configuration | `create-drop` | Disabled | In-memory H2 (`devflow-test`) |
| Production | `SPRING_PROFILES_ACTIVE=prod` | `validate` | Enabled | Required MySQL environment configuration |

The default profile validates but does not migrate; production must explicitly select `prod`. In production, Flyway applies versioned scripts before Hibernate validates the resulting schema. The test configuration overrides the datasource with H2 and never reads the production datasource variables.

### Dev-profile schema-update guard

The `dev` profile sets `ddl-auto=update`, but a `DevProfileSchemaGuard` bean-factory post-processor runs before datasource/JPA beans are created. Startup is rejected unless both conditions hold:

1. `DEVFLOW_DEV_DB_CONFIRMATION` is exactly `LOCAL_DATABASE_ONLY`.
2. The datasource URL is a parseable `jdbc:mysql://` URL whose host is `localhost`, `127.0.0.1`, or `::1`.

This is a fail-closed check; it does not print the datasource URL. It also rejects startup when `dev` and `prod` are active together. Configure the confirmation only in a local development launch configuration. The host check is a guardrail, not proof of database identity: a local port forward or proxy can point to a remote/production server. For a production database, use only the `prod` profile and a deployment configuration that cannot inherit the dev confirmation.

## Current migration and schema assumptions

`src/main/resources/db/migration/V1__user_enabled_and_email_uniqueness.sql` is a **draft incremental migration**, not a complete DevFlow database bootstrap script. It assumes an already-created legacy schema with a table named `user` and an `email` column. It adds the Phase 2B `enabled` column with a `TRUE` default and the named unique constraint `uk_user_email`. It intentionally does not edit any existing account values.

The actual `User` entity has no explicit `@Table(name=...)`; its current JPA mapping relies on Hibernate/Spring's physical naming strategy and the migration assumes that resolves to MySQL table `` `user` ``. The entity maps `email` as a default-length string with `nullable=false`, maps `enabled` as a non-null Boolean defaulting to true, and declares a unique constraint named `uk_user_email` on `email`.

The current project has no earlier Flyway migrations, no baseline schema script, and no production schema dump. Hibernate's prior `ddl-auto=update` may already have added `enabled` or an email unique constraint to a development or production database. The actual MySQL table name, column definitions, index/constraint name, collation, foreign keys, and state of all other DevFlow tables have not been verified. Therefore **the draft migration is not approved as safe to execute against an existing database**. It currently does not alter `email` to `NOT NULL` and does not set a case-insensitive collation or normalized unique index. MySQL unique indexes can allow multiple `NULL` values, and case comparisons follow the column collation. A fresh production database also is not supported by this incremental migration; a reviewed complete baseline schema is required first.

`V2__issue_acceptance_and_test_execution_details.sql` adds nullable `issue.acceptance_criteria` and nullable scenario/expected/actual result columns to `test_execution`. It widens `test_execution.notes` and `audit_log.details` to `TEXT`; it does not delete or rewrite existing records. Hibernate `update` applies these fields to a confirmed local development database, and H2 `create-drop` supplies them in tests. Production remains `validate` plus Flyway. V2 assumes the existing `issue`, `test_execution`, and `audit_log` tables and existing `notes`/`details` columns match the JPA schema. Inspect those exact definitions and `flyway_schema_history` before considering V2. It is not a fresh-database initializer, has not been run against MySQL, and is not approved for production until the database-specific Flyway adoption and rehearsal described below are complete.

`V3__user_profile_picture.sql` adds the nullable `user.avatar_key` column used to refer to profile pictures stored on disk outside the database and the static web root. It assumes the same mapped `user` table and existing Flyway sequence. It does not add/alter account rows or image files. For an existing schema, inspect `SHOW CREATE TABLE user` and `flyway_schema_history` and confirm whether `avatar_key` already exists before choosing a migration adoption/reconciliation path. Do not baseline or apply V3 against an unknown database state. For new/local development, the dev profile can add the column with Hibernate update; tests use isolated H2 create-drop.

Profile image bytes are stored under `DEVFLOW_PROFILE_PICTURE_DIR` in production. Set it to a durable, writable directory outside the application static-resource root, restrict OS permissions to the application account, and include that directory in backups. Local development uses `${user.home}/.devflow/profile-pictures` unless `DEVFLOW_PROFILE_PICTURE_DIR` is set. Images are PNG/JPEG only, capped at 5 MB and 4096×4096 pixels, and are served from the authenticated `/api/users/{id}/avatar` resource endpoint. The API returns only an opaque generated file key internally and a stable URL; image bytes are never stored in the database.

## Required non-destructive preflight

Use a read-only account and an authorized database tool. Never start the application in the `dev` profile against production. Back up the database and rehearse against a restorable clone with the same MySQL version, table definitions, and collation before any production change.

First verify the schema and database history:

```sql
SELECT DATABASE(), VERSION();

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('user', 'project', 'sprint', 'issue', 'comment',
                     'audit_log', 'notification', 'test_execution');

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
ORDER BY ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user';

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'flyway_schema_history';
```

Compare **all** existing tables and columns with the current JPA entities, not just `user`. Confirm the application’s physical naming strategy and the MySQL identifier/collation behavior. If a Flyway history table exists, inspect its rows using an authorized read-only account before proceeding.

Check for addresses that would collide after the application's trim/lowercase normalization, and for blank or null values:

```sql
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*) AS account_count
FROM `user`
WHERE email IS NOT NULL AND TRIM(email) <> ''
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;

SELECT id, email
FROM `user`
WHERE email IS NULL OR TRIM(email) = '';

SELECT id, email, LOWER(TRIM(email)) AS normalized_email
FROM `user`
WHERE email IS NOT NULL
  AND BINARY email <> BINARY LOWER(TRIM(email));

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME = 'email';
```

Do not proceed if the duplicate or blank/null query returns rows. DevFlow does not automatically delete, merge, or rewrite accounts. An authorized data owner must decide how to preserve and reconcile every affected account, then repeat the preflight. The case-variant query identifies stored addresses that are not already trimmed lowercase; each such account needs an explicit owner decision if canonicalization is required. Do not normalize existing addresses automatically. Confirm whether `enabled` and a unique email constraint already exist, including definitions and names. A unique index on `email` follows its MySQL collation; choose and test a case-insensitive uniqueness strategy against the actual MySQL version/collation. A duplicate check alone does not make V1 enforce case-insensitive uniqueness, and V1 does not enforce `email NOT NULL`. Those gaps require a reviewed migration change before production approval.

## Existing MySQL adoption procedure

Do these steps first on a restorable clone. Do not let the application perform the first migration during a production deployment.

1. Take and verify a backup. Run the preflight queries above. Compare the complete schema against the current entities and verify every table/column needed by this application exists.
2. Resolve duplicate normalized addresses and blank/null addresses only through an explicitly approved account-reconciliation process. Do not rely on Hibernate or the migration to change user data.
3. Inspect the existing Flyway history and classify schema/data state. A missing history table on a populated legacy database is not permission to auto-baseline:
   - **Fresh/empty production database:** stop. V1 is not an initializer. Create, review, and rehearse a complete baseline migration covering every entity/table, relationship, index, and constraint before choosing a starting version.
   - **Existing database with no Flyway history and neither `enabled` nor the email unique constraint:** V1 may be the intended next migration only after the actual table/column mapping is confirmed, data preflight is clean, the V1 nullability/collation gaps have been fixed in a reviewed migration, and a clone rehearsal passes. Then an operator may explicitly baseline the existing schema at version `0` and run the reviewed migration.
   - **Hibernate update already created both changes:** if the schema definitions fully match the approved target, inspect every entity/table and data invariant, then explicitly baseline at the version representing that exact state. Do not execute V1 again.
   - **Hibernate update created only part of the changes, or a column/constraint differs:** stop. A DBA must design and review a reconciliation migration and decide the versioning/adoption procedure. Do not baseline around an incomplete change or use `IF NOT EXISTS` to silently skip it.
   - **Duplicate, blank, null, or case-variant addresses:** stop. Obtain an account-owner decision for each affected record; no automated deletion, merging, or normalization is permitted. For case variants, confirm chosen MySQL collation/index semantics on a clone. A schema-only baseline does not resolve data conflicts.
4. Configure an approved Flyway CLI installation (including its MySQL JDBC driver) with the migration directory and protected environment values. In PowerShell, the environment-variable names below are what the CLI consumes; assignments copy the application's existing process variables and do not print their values:

   ```powershell
   $env:FLYWAY_URL = $env:DEVFLOW_DB_URL
   $env:FLYWAY_USER = $env:DEVFLOW_DB_USERNAME
   $env:FLYWAY_PASSWORD = $env:DEVFLOW_DB_PASSWORD
   $env:FLYWAY_LOCATIONS = 'filesystem:src/main/resources/db/migration'
   ```

   Use a controlled deployment identity with only the DDL privileges needed for the reviewed migration. Run `flyway validate`. For a legacy schema with no Flyway history, baseline at the version selected in step 3 (`flyway baseline -baselineVersion=0` when V1 must run; `flyway baseline -baselineVersion=1` only when the V1 schema is already fully present). Then run `flyway migrate` as a separately approved operation. Keep credentials out of command arguments, shell history, and logs.
5. Confirm `flyway_schema_history` records the intended version and success, inspect the resulting column/index/nullability/collation definitions, then start the application with `SPRING_PROFILES_ACTIVE=prod`. Hibernate `validate` must pass. Smoke-test the application on the clone and verify the backup/restore procedure before scheduling the production change.
6. For a brand-new production database, do not use this V1 script as an initializer. First create and review a complete baseline migration representing every DevFlow table, column, index, and foreign key; rehearse that baseline on an empty MySQL database, then establish the documented Flyway starting version.

The baseline version is a per-database decision based on inspected schema state; never enable `baseline-on-migrate` to make an unknown non-empty database proceed automatically. No migration, baseline, or production MySQL connection was run while implementing this change.

## Local configuration

Set the previously documented `DEVFLOW_DB_URL`, `DEVFLOW_DB_USERNAME`, `DEVFLOW_DB_PASSWORD`, and `DEVFLOW_JWT_SECRET` values in the process environment. For local development, activate only `SPRING_PROFILES_ACTIVE=dev` and set `DEVFLOW_DEV_DB_CONFIRMATION=LOCAL_DATABASE_ONLY`; the URL must point directly to a loopback MySQL host. For production, activate only `SPRING_PROFILES_ACTIVE=prod` and do not set the dev confirmation. Do not put actual credentials in this document, application properties, source control, terminal history, or screenshots. Maven tests use the checked-in H2 test properties and do not require or connect to MySQL.
