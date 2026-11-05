# DevFlow environment setup

This guide describes the configuration currently read by the DevFlow source. The backend and frontend remain separate projects. `.env.example` is a safe reference template, not a secret store: Spring Boot does not load it automatically. Do not replace existing IDE, Windows, or deployment settings with the example values.

## Required tool versions

| Tool | Requirement established from the project |
| --- | --- |
| Java | JDK 21 (`pom.xml` sets `java.version` to 21). Spring Boot is 4.1.1. |
| Maven | No global Maven install is required when using `mvnw.cmd`; the wrapper properties select Maven 3.9.16. |
| MySQL | MySQL 8.0 is the project’s target family. The repository does not pin a minimum server patch version; use the company-approved MySQL 8.0 release and rehearse migrations on a clone. MySQL 8.0.46 is the local version previously reported for this project, not a claim that migrations were verified here. |
| Node.js | Vite’s locked engine requirement is `^20.19.0 || >=22.12.0`. Use Node 22.12+ or 20.19+. |
| npm | `package-lock.json` uses lockfile version 3. Use npm 7 or newer; `npm ci` is the reproducible install command. |

## Backend environment variables

The backend reads these variables through `application.properties`, `application-dev.properties`, `application-prod.properties`, and Spring Boot configuration binding.

| Variable | Required / default | Purpose |
| --- | --- | --- |
| `DEVFLOW_DB_URL` | Required | JDBC URL for MySQL, for example `jdbc:mysql://localhost:3306/devflow_local_demo`. |
| `DEVFLOW_DB_USERNAME` | Required | MySQL account name. |
| `DEVFLOW_DB_PASSWORD` | Required | MySQL account password. Keep it in a local secret store or process environment; never commit it. |
| `DEVFLOW_JWT_SECRET` | Required | JWT HMAC secret; `JwtService` rejects values shorter than 32 UTF-8 bytes. Generate a random value for each environment. |
| `SPRING_PROFILES_ACTIVE` | Select explicitly | `dev` for local development or `prod` for production. Do not activate both. With no named profile, Hibernate validates the schema and Flyway is disabled. |
| `DEVFLOW_DEV_DB_CONFIRMATION` | Required for `dev` | Must be exactly `LOCAL_DATABASE_ONLY`. The dev profile also requires a loopback MySQL URL and uses Hibernate `update`; it can change that local schema. |
| `DEVFLOW_CORS_ALLOWED_ORIGINS` | Dev default: `http://localhost:5173`; required in `prod` | Comma-separated exact browser frontend origins. In production this is the frontend origin, not the backend API URL. Wildcard origins are not appropriate with credentials. |
| `DEVFLOW_PROFILE_PICTURE_DIR` | Dev default: `${user.home}/.devflow/profile-pictures`; required in `prod` | Writable local or mounted path for profile-picture files. |
| `DEVFLOW_ADMIN_EMAIL` | Optional | Bootstrap administrator email. Bootstrap occurs only when the email, password, and name are all supplied and no matching account already exists. Existing account password/role are left unchanged. |
| `DEVFLOW_ADMIN_PASSWORD` | Optional with the bootstrap trio | Initial password for a newly created bootstrap administrator. It is hashed by the application. Do not place it in source control. |
| `DEVFLOW_ADMIN_NAME` | Optional with the bootstrap trio | Display name for a newly created bootstrap administrator. |
| `DEVFLOW_API_DOCS_ENABLED` | Default `false` | Enables/disables OpenAPI and Swagger UI. The dev profile enables docs in its profile properties. |
| `DEVFLOW_API_DOCS_PUBLIC_ACCESS` | Default `false` | Controls public docs access; keep false unless there is a reviewed reason to expose docs. |
| `DEVFLOW_RATE_LIMIT_MAX_TRACKED_KEYS` | Default `100000` | Process-local rate-limiter capacity. |
| `DEVFLOW_LOGIN_RATE_LIMIT_MAX_REQUESTS` | Default `10` (dev profile: `20`) | Login-attempt limit per configured window. |
| `DEVFLOW_LOGIN_RATE_LIMIT_WINDOW` | Default `15m` | Login rate-limit window; use Spring duration syntax such as `15m`. |
| `DEVFLOW_AUTOMATION_POLL_DELAY_MS` | Default `1000` | Workflow automation polling delay in milliseconds. |
| `DEVFLOW_AUTOMATION_INITIAL_DELAY_MS` | Default `1500` | Workflow automation initial polling delay in milliseconds. |

Production profile behavior is explicit: `ddl-auto=validate`, Flyway enabled, and `baseline-on-migrate=false`. Do not start it against an existing database until that database’s schema and Flyway starting point have been verified. See [the database schema guide](docs/database-schema-management.md); the migration chain is incremental and must not be treated as a fresh-database initializer.

### Optional GitHub integration

GitHub is disabled by default (`devflow.github.enabled=false`) and is not needed to start DevFlow. Only configure it if the organization has approved GitHub App credentials. Spring environment-variable binding for the `devflow.github.app` configuration-properties fields follows [Spring Boot's documented canonical mapping](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables): replace dots with underscores, remove dashes, then uppercase:

| Environment variable | Corresponding property / use |
| --- | --- |
| `DEVFLOW_GITHUB_ENABLED` | `devflow.github.enabled`; set true to activate GitHub components. |
| `DEVFLOW_GITHUB_APP_APPID` | `devflow.github.app.app-id` |
| `DEVFLOW_GITHUB_APP_INSTALLATIONID` | `devflow.github.app.installation-id` |
| `DEVFLOW_GITHUB_APP_PRIVATEKEY` | `devflow.github.app.private-key`; provide through a secret manager/environment only. Literal `\\n` sequences are normalized by the token provider. |
| `DEVFLOW_GITHUB_APP_APIBASEURL` | `devflow.github.app.api-base-url`; defaults to `https://api.github.com`. |
| `DEVFLOW_GITHUB_APP_ALLOWEDENTERPRISEHOSTS` | `devflow.github.app.allowed-enterprise-hosts`; required only for an approved GitHub Enterprise API host. |
| `DEVFLOW_GITHUB_WEBHOOK_SECRET` | `devflow.github.webhook-secret`; required to validate webhook signatures when webhooks are used. |
| `DEVFLOW_GITHUB_WEBHOOK_MAXREQUESTBYTES` | `devflow.github.webhook.max-request-bytes`; defaults to 1,048,576 bytes. |

Never paste GitHub keys or webhook secrets into this file, source code, screenshots, or chat. Enterprise hosts must be explicitly allowlisted. GitHub is optional and must remain disabled until all approved credentials and webhook configuration are available.

## Frontend environment

| Variable | Required / default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Development defaults to `http://localhost:8080`; required for production build | Backend API base URL. A production build fails if this is missing, non-HTTPS, or local/loopback. |

Vite reads `.env`/`.env.local` from the frontend project directory, not from this parent folder. Inspect before creating `.env.local`; do not overwrite an existing file. Never put secrets in `VITE_` variables because they are embedded in the browser bundle. The frontend API URL and backend CORS origin are different values: the former is the backend address; the latter is the browser page’s origin.

## Local Windows setup (PowerShell)

### 1. Prepare a disposable local MySQL schema

Start MySQL 8.0 locally and connect with Workbench. First check whether `devflow_local_demo` already exists. Only if it does not exist and this is a new disposable local environment, create it:

```sql
CREATE DATABASE devflow_local_demo CHARACTER SET utf8mb4;
```

Do not drop, reset, or repurpose an existing `devflow`, `devflow_rehearsal`, or other database. Use a local MySQL account with the privileges needed for the dev profile to create/update tables. The `dev` profile has Hibernate `update` enabled and Flyway disabled; it is only for a confirmed loopback development database. It is not a production migration procedure.

### 2. Set process-scoped backend values

Open a fresh PowerShell window and set local values for that window only. Replace the username and password prompts locally; never paste credentials into chat. Generate a random JWT secret without printing it:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DEVFLOW_DB_URL = 'jdbc:mysql://localhost:3306/devflow_local_demo'
$env:DEVFLOW_DB_USERNAME = Read-Host 'Local MySQL username'
$secureDbPassword = Read-Host 'Local MySQL password' -AsSecureString
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureDbPassword)
try { $env:DEVFLOW_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr) }
$jwtBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes); $env:DEVFLOW_JWT_SECRET = [Convert]::ToBase64String($jwtBytes) }
finally { $rng.Dispose() }
$env:DEVFLOW_DEV_DB_CONFIRMATION = 'LOCAL_DATABASE_ONLY'
$env:DEVFLOW_CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
$env:DEVFLOW_PROFILE_PICTURE_DIR = Join-Path $env:USERPROFILE '.devflow\profile-pictures'
```

For a bootstrap admin, set all of `DEVFLOW_ADMIN_EMAIL`, `DEVFLOW_ADMIN_PASSWORD`, and `DEVFLOW_ADMIN_NAME` in the local process/IDE configuration. The password prompt and random JWT secret should not be written to committed files. These environment variables exist only in the current PowerShell process and child processes; close that window to discard them. IDE run configurations should use private local settings, not committed project files.

The PowerShell snippet generates a new JWT secret each time. That is suitable for a disposable local session; existing local JWTs will stop validating when the secret changes. For repeated local runs, retain one generated value in a private local IDE/environment setting. Never reuse a development JWT secret in production.

### 3. Start the backend

From the backend project directory (`devflow`):

```powershell
.\mvnw.cmd spring-boot:run
```

The backend listens on port 8080 by default. Confirm startup is using the intended disposable database before working with data. The dev profile’s Hibernate update is a schema mutation; never point it at a production or important existing database.

### 4. Start the frontend

Open a second PowerShell window. Set `VITE_API_BASE_URL` in that process if you do not want to rely on the development fallback, then start Vite from the separate frontend project directory:

```powershell
$env:VITE_API_BASE_URL = 'http://localhost:8080'
npm ci
npm run dev
```

Vite serves the UI at `http://localhost:5173` by default. For an already-installed local frontend, `npm run dev` is sufficient; `npm ci` recreates dependencies from `package-lock.json` and should be run only when an install is needed.

## Production startup notes

Set `SPRING_PROFILES_ACTIVE=prod` through the deployment platform’s secret/configuration service. Supply `DEVFLOW_DB_URL`, `DEVFLOW_DB_USERNAME`, `DEVFLOW_DB_PASSWORD`, `DEVFLOW_JWT_SECRET`, `DEVFLOW_CORS_ALLOWED_ORIGINS`, and `DEVFLOW_PROFILE_PICTURE_DIR`; configure `VITE_API_BASE_URL` as the deployed HTTPS backend API origin when building the frontend. Do not use localhost values in production. The deployed frontend origin and backend API origin are separate settings.

Production starts Flyway and validates the schema through Hibernate. Back up and rehearse the migration chain on a verified clone before rollout; the actual production schema starting point is an operational fact and cannot be inferred from these environment files. Do not set `baseline-on-migrate=true` to bypass an unknown history.
