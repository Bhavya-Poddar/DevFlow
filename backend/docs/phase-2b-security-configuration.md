# Phase 2B security and local configuration

## Required environment variables

The application requires these variables at startup; production properties contain no credential or JWT-secret fallback:

| Variable | Purpose |
| --- | --- |
| `DEVFLOW_DB_URL` | JDBC URL for the configured MySQL database |
| `DEVFLOW_DB_USERNAME` | MySQL account name |
| `DEVFLOW_DB_PASSWORD` | MySQL account password |
| `DEVFLOW_JWT_SECRET` | HMAC signing secret, at least 32 UTF-8 bytes |

The optional bootstrap account variables remain `DEVFLOW_ADMIN_EMAIL`, `DEVFLOW_ADMIN_PASSWORD`, and `DEVFLOW_ADMIN_NAME`. When absent, bootstrap is skipped.

In PowerShell, set the variables in the terminal before starting the backend. Replace each placeholder locally and do not paste actual values into source files, command history, chat, screenshots, or logs:

```powershell
$env:DEVFLOW_DB_URL = 'jdbc:mysql://localhost:3306/devflow'
$env:DEVFLOW_DB_USERNAME = '<your-mysql-user>'
$env:DEVFLOW_DB_PASSWORD = '<your-mysql-password>'
$env:DEVFLOW_JWT_SECRET = '<a-random-secret-of-at-least-32-bytes>'
```

These values apply only to processes launched from that terminal. Configure persistent values through your operating system's environment-variable settings or a secret manager. Never commit `.env` or `application-local.properties`; both are ignored by Git. The H2 test configuration is separate and is used by Maven tests.

Startup requires a resolvable database URL/user/password and a JWT secret of at least 32 bytes. Missing values or a weak signing key stop startup with a configuration error. SQL statement logging is disabled to avoid writing application data to logs.

## Database schema changes

Schema profiles, Flyway adoption, the Phase 2B `enabled` and email-uniqueness migration draft, and required preflight/rehearsal steps are documented in [database-schema-management.md](database-schema-management.md). Do not run the draft migration against an existing database until its actual schema and data have been inspected and a clone rehearsal has passed.

The account `enabled` column is an additive Boolean with a database default of `TRUE`, so existing records stay enabled when the column is added. An ADMIN may disable or re-enable another account by including `{"enabled":false}` or `{"enabled":true}` in `PUT /api/users/{id}`. An ADMIN cannot disable their own account through this endpoint. The endpoint remains ADMIN-only.
