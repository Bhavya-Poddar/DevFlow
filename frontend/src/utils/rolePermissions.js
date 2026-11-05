const moduleDefinitions = [
    { name: "Dashboard", keys: ["DASHBOARD_READ", "DASHBOARD_READ_ALL"] },
    { name: "Users", keys: ["USER_READ", "USER_MANAGE"] },
    { name: "Projects", keys: ["PROJECT_READ", "PROJECT_READ_ALL", "PROJECT_CREATE", "PROJECT_UPDATE", "PROJECT_DELETE", "PROJECT_ROLE_MANAGE"] },
    { name: "Private Teams", keys: ["TEAM_READ", "TEAM_READ_ALL", "TEAM_CREATE", "TEAM_MANAGE", "TEAM_ACTIVITY_READ", "TEAM_ROLE_MANAGE"] },
    { name: "Tasks", keys: ["ISSUE_READ", "ISSUE_CREATE", "ISSUE_UPDATE_ASSIGNED", "ISSUE_UPDATE", "ISSUE_UPDATE_ALL", "ISSUE_ASSIGN_DEVELOPER", "ASSIGNABLE_DEVELOPER", "ASSIGNABLE_TESTER", "ISSUE_STATUS_ADVANCE", "ISSUE_STATUS_MANAGE", "ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER", "ISSUE_DELETE", "ISSUE_DEPENDENCY_MANAGE"] },
    { name: "Sprints", keys: [], note: "Sprint endpoints use the corresponding Project permissions; no separate sprint permission exists." },
    { name: "Testing", keys: ["TESTING_QUEUE_READ", "TESTING_HISTORY_READ", "TEST_EXECUTE", "TESTER_ASSIGN", "TESTING_OVERRIDE"] },
    { name: "My Tests", keys: ["TESTING_MY_READ"] },
    { name: "Code Review", keys: [], note: "The review queue reads tasks and uses Tasks → View tasks. There is no separate Code Review permission." },
    { name: "Comments", keys: ["COMMENT_READ", "COMMENT_CREATE", "COMMENT_DELETE"] },
    { name: "Reports", keys: ["REPORT_READ"] },
    { name: "Activity", keys: [], note: "The current activity page reads available projects, tasks, and sprints; access follows those module permissions." },
    { name: "Audit Logs", keys: ["AUDIT_READ", "AUDIT_READ_ALL", "AUDIT_DELETE"] },
    { name: "Notifications", keys: [], note: "Personal notification endpoints are available to authenticated users; the catalogue has no role-level notification permission." },
    { name: "Automations", keys: ["AUTOMATION_MANAGE"] },
    { name: "Settings", keys: [], note: "Personal settings are available to authenticated users. Administrative settings require protected recovery access." },
    { name: "Admin Panel", keys: [], note: "Admin Panel access is controlled only by protected recovery capability and cannot be granted here." }
];

const visibilityKeys = new Set([
    "DASHBOARD_READ", "DASHBOARD_READ_ALL", "USER_READ", "PROJECT_READ", "PROJECT_READ_ALL",
    "TEAM_READ", "TEAM_READ_ALL", "TEAM_ACTIVITY_READ", "ISSUE_READ", "COMMENT_READ", "REPORT_READ",
    "AUDIT_READ", "AUDIT_READ_ALL", "TESTING_QUEUE_READ", "TESTING_MY_READ", "TESTING_HISTORY_READ"
]);

export function permissionScopeLabel(scope) {
    if (scope === "GLOBAL") return "Workspace-wide";
    if (scope === "PROJECT") return "Project-specific";
    if (scope === "PROJECT_TEAM") return "Project-team-specific";
    return "Scope unavailable";
}

export function permissionsGroupedByModule(catalogue, roleScope) {
    const available = new Map((Array.isArray(catalogue) ? catalogue : [])
        .filter(permission => permission?.key && permission.key !== "ROLE_MANAGE")
        .filter(permission => roleScope === "GLOBAL" || permission.scope === roleScope)
        .map(permission => [permission.key, permission]));
    return moduleDefinitions.map(module => ({
        ...module,
        permissions: module.keys.map(key => available.get(key)).filter(Boolean)
    }));
}

export function permissionKind(permission) {
    return visibilityKeys.has(permission?.key) ? "visibility" : "action";
}

export function editablePermissionKeys(catalogue, roleScope) {
    return permissionsGroupedByModule(catalogue, roleScope).flatMap(module => module.permissions.map(permission => permission.key));
}