# Workflow automation (first version)

DevFlow keeps task and testing logic in the existing Issue and TestExecution services. Those services write an `automation_event` outbox row in the same transaction as an actual status/test change. A scheduled worker (default poll delay: 1 second) processes committed rows and applies currently enabled rules. A failed event is retried with backoff up to five attempts; permanent failures and rule outcomes are visible through the execution API. Notification rows and rule executions commit together. WebSocket delivery is sent after the notification transaction commits.

## Supported events, conditions, and actions

Triggers: `TASK_STATUS_CHANGED`, `TESTING_PASSED`, `TESTING_FAILED`, and `BLOCKING_TASK_RESOLVED`. The only condition in v1 is an optional exact resulting issue status from the existing `IssueStatus` vocabulary. Actions are notification-only: `NOTIFY_ASSIGNED_DEVELOPER`, `NOTIFY_ASSIGNED_TESTER`, and `NOTIFY_UNBLOCKED_OWNERS`. No user code, scripts, status mutation, or recursive automation trigger is supported.

The first four examples are seeded idempotently at application startup. Edits to a built-in rule are preserved; deleting one leaves a hidden tombstone with its unique seed key so it stays deleted after restart. They notify the tester when a task reaches `READY_FOR_TESTING`, the developer on test failure/pass, and owners of tasks that become completely unblocked when a blocker resolves. Rules are manageable by ADMIN and PROJECT_MANAGER. Project has no manager/owner user relationship today, so a project-manager-recipient action is intentionally unavailable. No past events or execution rows are synthesized.

Event processing is idempotent per persisted event and rule (`automation_execution` has a unique event/rule key); outbox rows are protected by a unique source key and row locking across worker instances. Notifications use the existing Notification service and tables. A notification event itself does not create another automation event.

## API

- `GET /api/automations/rules` — list rules.
- `POST /api/automations/rules` — create a rule.
- `PUT /api/automations/rules/{id}` — replace editable rule fields.
- `PATCH /api/automations/rules/{id}/enabled` — body `{ "enabled": true }`.
- `DELETE /api/automations/rules/{id}` — remove a rule (past execution rows retain a rule-name snapshot).
- `GET /api/automations/executions?limit=50` — newest execution outcomes, capped at 100.

All routes require ADMIN or PROJECT_MANAGER both at URL authorization and service level. The optional `conditionStatus` must be an approved issue status. `NOTIFY_UNBLOCKED_OWNERS` is accepted only with `BLOCKING_TASK_RESOLVED`.

## Database rollout

V7 creates `automation_rule`, `automation_event`, and `automation_execution`; V8 adds the `automation_rule.deleted` tombstone flag with an existing-row-safe default of false. Neither migration alters existing task/notification data, inserts historical events, contacts MySQL, or runs automatically in this development session. Production Flyway remains governed by the existing baseline process; review the schema-management guide and establish the actual database's migration baseline before applying V7/V8. Do not set baseline version 6 or execute these migrations until the existing V1–V6 schema has been verified and the migration plan approved. Development's existing Hibernate-update profile can create/update the entities locally; tests continue using isolated H2 create-drop.

## Current limitations

- Delivery runs in a database-backed worker in each app instance. Row locks and unique constraints protect duplicate execution, but external WebSocket delivery is best-effort; persisted notifications remain the source of truth.
- Events retry five times, then appear as a failed event-processing execution. Operators should inspect the safe failure class and backend logs before retrying or correcting configuration.
- Project manager recipient targeting waits for a project membership/manager relationship in the data model.
- V7/V8 are untested against the user's MySQL database and have not been applied. Run H2 tests and browser checks, then rehearse them on a verified clone before any production rollout.
