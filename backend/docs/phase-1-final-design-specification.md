# DevFlow Phase 1 — Final Design Specification

**Status:** Approved design; implementation has not started.
**Architecture:** Preserve DevFlow as a modular monolith: one Spring Boot application, one relational database, and a separate React/Vite frontend.
**Scope:** Configurable roles and business options, admin-managed accounts, project-specific teams and scoped authority, staged task approval, standardized forms, and safe migration from existing data.

This records the approved Phase 1 and Phase 1.1 decisions. It is a target design, not a claim that the behavior or schema is implemented.

## 1. Existing architecture and reusable components

The backend uses controller, service, repository, DTO, entity, security, and configuration layers. Services coordinate task lifecycle, testing, audit, notifications, automation, and project access. The frontend is a separate React/Vite client.

Existing capabilities to retain and extend:

- JWT authentication, password hashing, current-account enabled/role revalidation, and safe user response DTOs.
- ProjectAccessService and ProjectPrivacyInterceptor for server-side project/team access checks.
- Project, team, issue/task, sprint, test-execution, audit, activity, notification, and automation services.
- Existing audit and notification mechanisms for administrative and workflow events.
- Versioned Flyway migrations and production Hibernate validation.

The current model has a global User.role string, a Team with one manager and a member set, and an optional Project.team association. Team memberships do not carry scoped roles. A task is persisted as Issue and currently has one assigned user. The current test-pass path resolves a task immediately; the target workflow intentionally changes that behavior.

Current teamless projects are globally visible under the existing access rule. Preserve existing project visibility during migration until data and policy are reviewed. Existing public registration creates VIEWER accounts and must be disabled for the target account lifecycle.

## 2. Product and authorization principles

1. **Backend authorization is authoritative.** Hiding a link or button in React is not access control.
2. **Permissions are bounded.** Admins configure roles from a server-supported permission catalogue; they cannot create executable permissions, arbitrary routes, or backend behavior.
3. **Scopes are explicit.** Global, project, and project-team authority are distinct. A project manager on one project is not a global administrator.
4. **Technical workflow identifiers remain protected.** Display configuration may be editable, but stable state and permission keys cannot be redefined through generic master-data APIs.
5. **Decision history is append-only.** Retain testing, review, role/permission, and relevant assignment history.
6. **Migration preserves existing access and data.** Do not infer a production schema version from entity mappings or table names.

## 3. Database relationship design

The design extends existing tables. Exact SQL types, FK names, indexes, and backfills are determined during implementation and clone rehearsal.

### 3.1 Identity and authorization

- **user:** existing account identity, enabled state, password hash, and profile fields. Add first-login/reset lifecycle state as needed; never store plaintext credentials or reset tokens.
- **role:** stable code, display name, scope type, system/retired metadata, timestamps.
- **permission_catalogue:** stable permission key, description, and allowed scope: GLOBAL, PROJECT, or PROJECT_TEAM. This is application-supported metadata, not user-authored executable logic.
- **role_permission:** unique role/permission grants; restrict deletion of protected catalogue entries.
- **user_global_role:** global role assignment where required. Map current global roles into equivalent role records during transition.
- **project_membership:** user, project, role, active state, and timestamps; supports multiple project managers and project-scoped roles.
- **project_team:** association of a team identity to a project.
- **project_team_membership:** project-team, user, scoped role, active state, and timestamps. Users may belong to multiple project teams and hold different roles in each.

Role and membership FKs must not cascade-delete assignments. Account deactivation is preferred to deleting users.

### 3.2 Project teams and leadership

Keep a team identity/name and associate it to a project through project_team. Project creation can select an existing eligible team or create one inline, then attach the association transactionally. A project may remain teamless. Inline-created teams appear in the Private Teams area after successful creation.

Membership, manager, and team-lead roles are scoped to the project-team association, not globally granted through a team name. A manager must be a member of the association they manage. Enforce that invariant transactionally.

This allows existing-team selection at project creation while keeping roles project-specific. It also supports multiple teams per project and per-user roles differing by team.

### 3.3 Master configuration

- **configuration_group:** group key, label, editability policy, metadata.
- **configuration_option:** stable option code, group, display label, enabled state, display order, and timestamps/actor.
- Business records reference stable codes/IDs, not mutable labels. Preserve historical labels where audit/report fidelity requires it.

### 3.4 Task workflow and history

Retain issue as the task record and preserve existing project, sprint, assignment, dates, dependencies, and descriptions. Retain test_execution for scenarios, outcome, notes, tester, and timestamps.

Add an append-only workflow cycle per submission/resubmission and workflow decision records containing issue, cycle, stage, decision, actor, remark, accountable-developer snapshot, and timestamp. Index by issue/cycle/time. Reuse audit logs for cross-cutting events; do not duplicate detailed test/approval history in a second generic event table.

## 4. Dynamic roles, permissions, and recovery

### 4.1 Permission evaluation

Resolve effective permissions from the authenticated user and target resource:

1. Load current enabled account state and global assignments.
2. For a global endpoint, evaluate only global permissions.
3. For a project resource, verify project membership or the explicit legacy visibility policy, then evaluate project-scoped grants.
4. For project-team resources, verify membership in that exact association and evaluate its scoped grants.
5. Apply administrator overrides only where explicitly defined in the catalogue. Never infer that PROJECT_MANAGER means global access.

Use the same policy service for controllers and services. React may consume an effective-permissions endpoint for navigation/action visibility, but backend checks remain authoritative.

### 4.2 Role management

Admins can create and configure all business roles, including Admin, using supported permission keys. Permission keys and scopes are defined by the backend catalogue. Audit role creation, edits, assignments, retirement, and permission diffs. Permission evaluation must reflect current account permissions on subsequent requests, not stale token claims.

### 4.3 Protected administrative recovery

Recovery capability is protected independently of ordinary editable role permissions:

- A dedicated recovery capability is not removable from the catalogue or ordinary role-permission UI.
- Before committing any role, assignment, or enabled-state change, verify that at least one enabled account retains effective role-management and recovery capability.
- Serialize relevant changes or use equivalent locking so concurrent changes cannot jointly remove the final capable account.
- Normal Admin role editing remains possible if another enabled recovery-capable account remains after the transaction.
- No unauthenticated HTTP recovery endpoint is permitted.
- For invariant violations caused by manual corruption or operational failure, document an offline break-glass operation requiring host/operator access and a one-time secret from the deployment secret store. It grants capability to a named existing enabled account and creates an audit record when restored; it does not bypass application login. If no enabled account can authenticate, a controlled restore from verified backup may be required.

### 4.4 Role retirement and reassignment

Retirement blocks new assignments while preserving the role and existing references. A role with assignments cannot be hard-deleted. Admins explicitly select replacement roles and principals; never use automatic fallback or cascading reassignment. Validate scope compatibility and show permission changes, requiring confirmation for privilege increases. Prefer retaining retired roles for audit continuity.

## 5. Account creation and password lifecycle

- Disable public self-registration (currently POST /api/auth/register).
- Authorized admins create accounts and assign initial role and project/project-team memberships through one validated workflow.
- Never email a reusable password. Use a short-lived, single-use invitation/reset token; store only its hash and deliver the raw token through an approved channel, never logs or API history.
- Require first-login password change server-side before normal application use.
- Reuse existing secure password hashing.
- Admins can deactivate/reactivate accounts and initiate resets. Deactivation invalidates subsequent authenticated requests, including requests using previously issued tokens.
- Preserve authenticated self-service password change with current-password verification.
- Audit account creation, grants, deactivation, and reset initiation without recording passwords or tokens.

## 6. Project-specific teams, managers, and leads

A project can have zero or more project-team associations. Project creation offers existing team selection and inline creation. A user may belong to multiple associations with different roles. Managers and leads are assigned per project or project-team; membership and leadership grants are separately validated.

Managers can access only projects explicitly assigned to them, within their scoped permissions. Project managers do not inherit Admin authority. All project-scoped task, sprint, dashboard, report, activity, comment, dependency, and team routes use the centralized access evaluator.

Preserve current teamless-project visibility during initial migration. Restricting it later requires an approved policy and reviewed data backfill.

## 7. Complete task workflow

### 7.1 State sequence

Developer work → submit for testing → Tester testing
• Fail with required remark → Developer work
• Pass → Team Lead review
  • Reject with required remark → Developer work
  • Approve → Manager review
    • Reject with required remark → Developer work
    • Confirm Done with required remark → Resolved

Canonical status keys remain stable. Passing a test no longer directly resolves a task; this deliberately changes current TestExecutionService behavior. Resolved tasks appear in a separate Tasks section using the same task records.

### 7.2 Decisions and assignment

- Only the authorized assigned tester (and any explicitly preserved Admin override) may start/complete testing.
- Only assigned project-team leads may decide at lead review.
- Any active project manager assigned to the project may approve/reject manager review. The **first valid decision wins**; stale concurrent decisions return a conflict.
- Rejection at every stage requires a nonblank remark and returns the task to developer work.
- Keep one accountable developer through the existing single assignee. Snapshot that developer at submission and return rejected work to that cycle’s owner.
- Freeze assignment during testing/review. An authorized manager may explicitly reassign as a separate audited action; do not silently rewrite the cycle owner.
- If multiple contributors are later needed, model them separately while retaining one accountable developer.
- Resubmission starts a new cycle and captures the current accountable developer. Previous test and decision history is immutable.

### 7.3 Transition safeguards

Backend rules own transitions and stage permissions. Direct status updates cannot bypass testing or approval. Lock/version-check decisions to prevent duplicate transitions. Persist outcome, decision, current status, audit, and required notifications consistently. Never erase old test runs or rejection remarks.

## 8. Master configuration and protected values

| Option | Admin may | Protected boundary |
|---|---|---|
| Project/task categories | Add, rename label, reorder, disable | Stable key; no hard delete while referenced |
| Priorities | Add, edit label, reorder, disable | Stable code and historical meaning |
| Menu entries | Relabel, reorder, enable/disable allowlisted route keys | No arbitrary routes/URLs; server permissions still apply |
| Canonical statuses | Change label/color and safe presentation order | State key, transitions, and lifecycle guards |
| Custom statuses | Add only through validated workflow configuration | Must define legal transitions and cannot bypass required stages |
| Permission keys, audit actions, test outcomes | Not editable as business options | Backend-owned catalogue |
| JWT/database/CORS/GitHub credentials and endpoints | Not generic master data | Deployment secrets or specialized security configuration |

Disabling never rewrites existing records. Existing records continue displaying the retired option. New selections are rejected. Unrelated partial updates preserve an existing disabled value. Renaming changes display only, not stable key.

## 9. API design targets

These are future contract targets, not claims that these routes currently exist.

- **Configuration:** read option groups; Admin create/update/enable/disable/reorder business options.
- **Roles:** read supported permission catalogue; Admin list/create/update/retire roles and grant supported permissions; require explicit reassignment before deletion.
- **Effective authorization:** current-user permissions for frontend rendering; backend evaluates every operation independently.
- **Accounts:** Admin create/invite, deactivate/reactivate, reset, and initial membership assignment; user invitation acceptance and first-login password change.
- **Project teams:** list/create, add/remove members, update scoped roles, assign/unassign to projects, and create inline during project creation.
- **Leadership:** assign/remove scoped managers and team leads.
- **Workflow:** submit for testing, start/complete test, lead approve/reject, manager approve/reject/confirm Done. Rejection requires remarks; decisions use concurrency/version checks.
- **Tasks:** retain existing task APIs where compatible; enforce centralized authorization and canonical transitions; add a resolved filter/section rather than a duplicate task entity.
- **Audit:** extend existing audit access for security events while preserving restricted access.

Mutation DTOs must omit server-controlled IDs, actor, timestamps, and calculated permissions. Derive actor from the authenticated principal.

## 10. Standardized frontend forms

Use shared form components and field ordering:

1. Selects and relationships first (project, team, role, status, priority).
2. Short text, number, and date inputs.
3. Long descriptions, acceptance criteria, and remarks.
4. Actions at the bottom with consistent cancel/save, disabled/loading, and validation states.

Create forms include relevant supported fields. Routine edits use focused actions; a separate Edit Details action handles multi-field edits. Backend validation remains mandatory. Existing disabled options display on existing records but are unavailable for new selections.

## 11. Migration and backward compatibility

The repository contains incremental Flyway migrations through V12. That chain does not prove an arbitrary live database is at any version. Production uses Flyway and Hibernate validation. Never infer a baseline or rewrite an applied migration.

1. Obtain a verified restorable backup/clone and compare schema plus Flyway history to the exact source revision.
2. Rehearse migrations and backfills on a disposable clone.
3. Add new versioned migrations for role/permission, scoped memberships, configuration, and workflow history. Do not modify prior migrations.
4. Map current fixed roles to role records without changing effective access.
5. Map existing Team, team_member, and Project.team_id data into project-team associations. Preserve memberships and current teamless visibility until mapping is verified. Resolve ambiguous/orphaned references on the clone; do not silently discard data.
6. Seed stable business-option codes matching existing values. Preserve unknown values as retired/legacy options, not coerced values.
7. Create workflow history only for future decisions; do not invent historic approval events. Existing executions remain test history.
8. Deploy in compatible stages: additive schema, backend compatibility, reviewed backfill, permission/UI migration, then remove legacy paths after verified usage.
9. Define schema and data recovery. Use a verified backup restore for destructive or ambiguous backfills; reverse DDL alone may not restore data.

## 12. Acceptance criteria

### Dynamic roles and permissions
- Admin creates/edits roles, including Admin, using only supported keys.
- Project/team grants work only in their scope; direct API checks enforce them.
- Changes are audited and effective on subsequent requests.
- No committed change leaves zero enabled recovery-capable accounts; concurrent changes are covered.
- Offline recovery requires operator access and has no unauthenticated HTTP route.

### Account lifecycle
- Public registration is unavailable.
- Only authorized admins create accounts and initial assignments.
- Invitation/reset tokens are single-use, time-limited, hashed at rest, and absent from logs/responses.
- First-login change is enforced server-side.
- Deactivation blocks subsequent requests using existing tokens.

### Project teams and leadership
- Projects may be teamless or have project-team associations.
- Inline-created teams appear after successful project creation.
- Users can have multiple project teams and different roles in each.
- Multiple managers/leads remain scoped.
- Cross-project/team direct API access is denied; legacy visibility is preserved until reviewed migration.

### Workflow
- Authorized tester records pass/fail after developer submission.
- Pass goes to Team Lead review and does not resolve.
- Lead approval advances to Manager review.
- Rejections at all stages require remarks and return work to the cycle’s accountable developer.
- Any active assigned project manager can make the final decision; first valid decision wins.
- Only manager confirmation with required remark resolves.
- Resubmission starts a new cycle and retains history.
- Resolved tasks are separately discoverable under normal authorization.

### Configuration and forms
- Admin manages approved business options without code changes/redeploy.
- Protected identifiers cannot be altered through generic option APIs.
- Disabling leaves existing records unchanged and blocks new selection.
- Forms use shared field order and expose only supported, authorized values.

## 13. Implementation phases in dependency order

1. **Authorization foundation:** permission catalogue, evaluator, scopes, audit events, recovery invariant, concurrency tests.
2. **Schema compatibility:** additive migrations, clone rehearsal, role/team backfill design, preserve visibility.
3. **Account lifecycle:** disable registration; implement invitation, first-login change, reset, deactivation, recovery operations.
4. **Scoped teams and leadership:** project-team associations, multiple managers/leads, scoped membership, inline creation, full API checks.
5. **Workflow history/transitions:** cycles, testing-to-lead-to-manager, rejection remarks, assignee snapshots, concurrency, resolved section.
6. **Master configuration:** option APIs/UI, stable seeds, protected identifiers, disabled-option behavior.
7. **Frontend consolidation:** effective-permission navigation/actions and shared forms.
8. **Release verification:** RBAC/workflow tests, full Maven suite, frontend lint/build, migration rehearsal on verified clone, then authorized browser/runtime checks.

## 14. Unresolved technical risks

- Deployment schema/history may not match Flyway; verify on a clone before rollout.
- Existing team/project data may not map unambiguously; preserve rows and visibility until reconciled.
- Teamless projects remain globally visible for compatibility, which is broad access.
- Mapping global roles to scoped permissions can change access if incomplete; compare effective access before/after.
- The approval flow changes current pass-means-resolved behavior; review clients/reports relying on it.
- Break-glass operation depends on secure operator access and secret-store procedures; test without exposing the secret.
- Custom status semantics affect validation, automation, reporting, and permissions; defer until all are specified and tested.

## 15. Confirmed decisions

- DevFlow remains a modular monolith.
- Admin can configure all business roles, including Admin, within the supported permission catalogue.
- Recovery capability is independent of ordinary editable role permissions; no unauthenticated recovery endpoint.
- At least one enabled recovery-capable account is preserved transactionally.
- Assigned roles are retired and explicitly reassigned; no cascade or automatic fallback.
- Any active project manager assigned to a project can make final manager-stage decisions; the first valid decision wins.
- Project managers have no global Admin authority by virtue of project assignment.
- A task has one accountable developer; rejection returns it to the workflow-cycle assignee and preserves history.
- Projects may remain teamless; team membership and leadership are project-scoped.
- Business options are configurable; workflow identifiers, permission keys, and security values are protected.
- No historic approval events are fabricated; schema changes require reviewed clone rehearsal.
