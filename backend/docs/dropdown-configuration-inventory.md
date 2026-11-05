# DevFlow selectable-option inventory

This inventory is based on the active frontend source under `src/pages`. The audit found **67 `<select>` controls in 13 page files**, representing **20 distinct option categories** in the Admin Panel catalogue. Repeated selectors are counted once by category. User/project/team/sprint/task/role selectors are record selectors, not duplicate static option lists.

## Controls by page

| Page | Select count | Fields represented |
| --- | ---: | --- |
| Admin Panel | 18 | Initial/global/scoped role, account status, team, project, project/team role, role scope, dropdown category, menu permission |
| Audit Logs | 3 | Project, audit action, task |
| Automations | 3 | Trigger, action, condition status |
| Code Reviews | 2 | Priority, project |
| Comments | 3 | Project, task status, task |
| Dashboard | 2 | Project, sprint |
| Issues (Tasks) | 16 | Status/priority/project filters; create and edit project, priority, status, sprint, developer, tester; blocker task |
| Private Teams | 1 | Registered person |
| Projects | 3 | Project category, private team, project status |
| Reports | 6 | Developer, sprint, date range, task status, test result, tester |
| Sprints | 5 | Sprint board selector, sprint status, project (create/edit) |
| Team | 2 | Role filter, role assignment |
| Team Workload | 3 | Role, workload level, project |

## Distinct categories and source

| Category / field | Pages and forms | Current option source and values | Workflow / storage | Admin treatment |
| --- | --- | --- | --- | --- |
| **Task priority** (`PRIORITY`) | Tasks create/edit/filter/list; Sprints board; Code Reviews; Reports | `admin_dropdown_option`; V14 seeds LOW, MEDIUM, HIGH, CRITICAL; later custom values are data-backed | Stored as task/review values; validates enabled options | **Full management**: add, rename, reorder, disable/enable, delete only when unused and at least one enabled choice remains |
| **Project category** (`PROJECT_CATEGORY`) | Projects create/edit | `admin_dropdown_option`; Project Category options are existing database configuration (not enumerated here because no live DB was inspected) | Stored on project; validation accepts enabled configured code | **Full management** with the same safe deletion/last-enabled guards |
| **Task status** (`ISSUE_STATUS`) | Tasks status filter/create/edit; Comments status filter; Reports; Automation condition; task status labels | `IssueStatus` enum and task workflow; V17/initializer seeds OPEN, IN_PROGRESS, CODE_REVIEW, READY_FOR_TESTING, TESTING, RESOLVED | Controls transitions/testing/resolution; persisted in `issue.status` | **Label/order only**. IDs, add/delete/enable, and transition behavior remain protected because the backend transition engine is enum-backed |
| **Project status** (`PROJECT_STATUS`) | Projects create/edit and list/detail | Existing supported project statuses; V17/initializer seeds ACTIVE, IN_PROGRESS, COMPLETED | Persisted project state; application behavior uses fixed supported values | **Label/order only**; state identifiers protected |
| **Sprint status** (`SPRINT_STATUS`) | Sprints create/edit/list | Existing supported sprint statuses; V17/initializer seeds PLANNED, ACTIVE, COMPLETED | Persisted sprint state and active-sprint selection | **Label/order only**; state identifiers protected |
| **Test result** (`TEST_RESULT`) | Test Workspace pass/fail actions/history; Reports result filter | V17/initializer seeds PASSED and FAILED | Drives testing outcome and issue lifecycle | **Label/order only**; outcome keys and pass/fail behavior protected |
| **Automation trigger** (`AUTOMATION_TRIGGER`) | Automation rule form | `AutomationTriggerType`; TASK_STATUS_CHANGED, TESTING_PASSED, TESTING_FAILED, BLOCKING_TASK_RESOLVED; seeded by V17/initializer | Selects backend event handlers | **Label/order only**; event keys protected |
| **Automation action** (`AUTOMATION_ACTION`) | Automation rule form | `AutomationActionType`; NOTIFY_ASSIGNED_DEVELOPER, NOTIFY_ASSIGNED_TESTER, NOTIFY_UNBLOCKED_OWNERS; seeded by V17/initializer | Selects backend actions | **Label/order only**; action keys protected |
| **Account status** (`ACCOUNT_STATUS`) | Admin user filter and account editor | `User.enabled`; ACTIVE/INACTIVE labels seeded by V17/initializer | Security-sensitive account activation | **Label/order only**; status changes stay in user management and authorization controls |
| **Reporting range** (`REPORTING_RANGE`) | Reports range filter | V17/initializer seeds 30, 90, ALL | Fixed range calculation values | **Label/order only**; adding values requires explicit supported range semantics |
| **Workload level** (`WORKLOAD_LEVEL`) | Team Workload filter and member cards | Fixed LOW/MEDIUM/HIGH presentation thresholds (0–3, 4–7, 8+) | Presentation only; no database field | **Label/order only**; thresholds and keys are protected |
| **People / users** (`USER_RECORD`) | Admin user/membership forms; task developer/tester; Private Teams; Reports/Audit selectors | Existing registered-user APIs, filtered by role/context/permission | Relational records and assignments | **Record selector**; manage users/memberships in their existing administration sections |
| **Projects** (`PROJECT_RECORD`) | Admin membership forms; task forms/filters; Projects; Dashboard; Reports; Sprints; Code Reviews; Audit Logs; Team Workload | Existing authorized project records | Relational selector; visibility remains backend-authorized | **Record selector**; manage project records in Projects |
| **Teams** (`TEAM_RECORD`) | Admin membership forms; project team assignment | Existing team records | Relational membership/ownership | **Record selector**; manage teams in Private Teams |
| **Sprints** (`SPRINT_RECORD`) | Task forms; Dashboard; Reports; Sprints board | Existing sprints, limited by project context where applicable | Relational selector | **Record selector**; manage sprint records in Sprints |
| **Tasks** (`TASK_RECORD`) | Blocker linking; Comments; Audit Logs | Existing authorized task records | Relational selector; task visibility/authorization applies | **Record selector**; manage tasks in Tasks |
| **Roles** (`ROLE_RECORD`) | User creation/management and membership assignment; Team | Existing role definitions | Security assignments | **Record selector**; manage roles in Manage Roles |
| **Role scope** (`ROLE_SCOPE`) | Manage Roles | Fixed GLOBAL / PROJECT / PROJECT_TEAM permission scopes | Authorization boundary | **Protected**; never ordinary configurable options |
| **Permission key** (`PERMISSION_KEY`) | Role permissions/menu visibility | Backend permission catalogue | Authorization identifiers | **Protected**; catalogue remains backend-supported |
| **Audit action** (`AUDIT_ACTION`) | Audit Logs | Distinct action values found in audit history | Historical event data | **Data-derived**; not editable to preserve truthful historical meanings |

## Coverage and limitations

- The central API is backed by the existing `admin_dropdown_option` table. V17 adds missing fixed-code display rows with `INSERT IGNORE`; the application initializer also inserts only absent rows and preserves administrator-edited labels.
- The frontend loads configured values in Projects, Tasks, Sprints, Testing, Code Reviews, Comments, Reports, Automations, Admin user status, and Team Workload. Record selectors continue to load real records from their existing APIs.
- Only Task Priority and Project Category support arbitrary new option codes today. Statuses, test outcomes, automation triggers/actions, account state, reporting ranges, and workload thresholds have code-level behavior or fixed semantics; they are centrally relabelable/reorderable but do not support arbitrary values without changing business logic.
- Project-category values and the live option counts cannot be verified without inspecting the database. No database was accessed for this inventory.
- V17 has **not** been applied to any database by this work.
