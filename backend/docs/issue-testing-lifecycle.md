# Issue lifecycle and testing workflow

## Approved issue statuses

`Issue.status` remains a database `String`; no database type conversion or migration is part of this phase. The canonical API/service vocabulary is centralized in `IssueStatus`:

- `OPEN`
- `IN_PROGRESS`
- `CODE_REVIEW`
- `READY_FOR_TESTING`
- `TESTING`
- `RESOLVED`

Inputs are trimmed and normalized to uppercase. Missing status during issue creation defaults to `OPEN`. Blank, malformed, unknown, and legacy values are rejected by ordinary create/update requests and by the report status filter. An existing row with an unknown non-null legacy value is not rewritten. A status-changing update of that row returns a conflict pending review; unrelated partial updates can preserve the stored value. A pre-existing null/blank status retains the old developer-transition interpretation as `OPEN` for compatibility; no row is updated just by reading it.

## Transition matrix

| Actor / operation | Allowed transition | Notes |
| --- | --- | --- |
| Create issue | Default `OPEN`; explicit `OPEN`, `IN_PROGRESS`, `CODE_REVIEW`, or `READY_FOR_TESTING` | Preserves existing ability to initialize into a manually editable state. `TESTING` and `RESOLVED` are reserved to testing operations. |
| DEVELOPER update | `OPEN → IN_PROGRESS → CODE_REVIEW → READY_FOR_TESTING` | Only on an issue assigned to the authenticated developer; no assignment/project/sprint/tester changes. Same-status field updates are allowed. |
| ADMIN / PROJECT_MANAGER update | Any transition among `OPEN`, `IN_PROGRESS`, `CODE_REVIEW`, and `READY_FOR_TESTING`, including reopening `RESOLVED` into a manual state | Preserves the existing broader manager/admin editing behavior. No manual transition into `TESTING` or `RESOLVED`. An issue currently `TESTING` cannot be status-edited. |
| Assigned TESTER starts testing | `READY_FOR_TESTING → TESTING` | Uses `POST /api/testing/{issueId}/start`; tester must be enabled, have exact role `TESTER`, and match the issue tester. |
| Assigned TESTER passes testing | `TESTING → RESOLVED` | Uses `POST /api/testing/{issueId}/complete` with `PASSED`; execution, result, and issue state are transactional. |
| Assigned TESTER fails testing | `TESTING → IN_PROGRESS` | Uses completion with `FAILED` and nonblank failure notes; execution, result, notes, and issue state are transactional. |

`PUT /api/issues/{issueId}/tester` remains ADMIN/PROJECT_MANAGER-only and accepts only a user with exact role `TESTER`. Tester assignment cannot change while the issue is `TESTING`.

## Status inventory and compatibility

- `OPEN`, `IN_PROGRESS`, `CODE_REVIEW`, `READY_FOR_TESTING`, `TESTING`, and `RESOLVED` are the approved issue lifecycle states. They are used by issue creation/update, developer transition checks, test queues, test start/completion, issue responses, and project report output/filtering.
- `TODO` was found in old notification test fixtures only. Those fixtures now use `OPEN`. It is not an alias and is rejected by issue create/update/report filters.
- `CLOSED` was found in the project report summary calculation as a historical completed count. It remains counted as completed if already present in stored data, but it is not accepted for new issue creation, updates, or report filters. No stored rows were inspected or rewritten.

**Product decision still required:** decide whether historical `TODO` should map to `OPEN` or `IN_PROGRESS`, and whether historical `CLOSED` should map to `RESOLVED`, remain a distinct archival state, or be retired. This phase intentionally makes no data conversion. Before a future cleanup, inspect actual database values and obtain a reviewed per-row mapping; do not assume MySQL data contains only canonical values.

## Status-bearing APIs and implementation

- `POST /api/issues` creates issues, with status defaulting to `OPEN`.
- `PUT /api/issues/{id}` performs partial updates and validates any supplied status.
- `POST /api/testing/{issueId}/start` and `POST /api/testing/{issueId}/complete` are the only supported ways to enter `TESTING` or `RESOLVED`.
- Project report status filtering accepts only canonical issue statuses. Report response DTOs continue exposing the stored string so historical values remain visible for review.

`IssueStatus` centralizes the allowed values and transition predicates without changing the database column. `TestExecution.status` is a separate history state (`IN_PROGRESS` / `COMPLETED`), and test result is separate (`PASSED` / `FAILED`); neither is an issue status.

## Remaining risk

The application has not inspected any MySQL rows. Legacy or malformed stored statuses may therefore remain visible in responses/reports and can prevent a status transition until reviewed. Existing reports continue counting `CLOSED` as completed for compatibility, which should be revisited after the product decision. No frontend changes are included; frontend status choices should use the six canonical values.
