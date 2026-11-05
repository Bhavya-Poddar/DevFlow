package com.devflow.devflow.service;

import java.util.List;
import com.devflow.devflow.entity.PermissionDefinition;
import com.devflow.devflow.entity.PermissionScope;

/** Fixed set of capabilities that the application knows how to enforce. */
public final class PermissionCatalogue {
    public record Entry(String key, String description, PermissionScope scope) {}
    private static final List<Entry> ENTRIES = List.of(
        e("USER_MANAGE", "Create and manage user accounts", PermissionScope.GLOBAL),
        e("USER_READ", "Read the administrative user directory", PermissionScope.GLOBAL),
        e("PROJECT_CREATE", "Create projects", PermissionScope.GLOBAL),
        e("PROJECT_READ_ALL", "Read all projects, including private projects", PermissionScope.GLOBAL),
        e("TEAM_CREATE", "Create teams", PermissionScope.GLOBAL),
        e("TEAM_READ_ALL", "Read all teams", PermissionScope.GLOBAL),
        e("AUDIT_DELETE", "Delete audit records", PermissionScope.GLOBAL),
        e("AUDIT_READ_ALL", "Read unscoped audit history", PermissionScope.GLOBAL),
        e("DASHBOARD_READ_ALL", "Read unscoped dashboard metrics", PermissionScope.GLOBAL),
        e("TESTING_OVERRIDE", "Override assigned-tester ownership checks", PermissionScope.GLOBAL),
        e("ASSIGNABLE_DEVELOPER", "May be assigned as accountable developer", PermissionScope.GLOBAL),
        e("ASSIGNABLE_TESTER", "May be assigned as tester", PermissionScope.GLOBAL),
        e("PROJECT_READ", "Read projects", PermissionScope.PROJECT),
        e("PROJECT_UPDATE", "Update project details", PermissionScope.PROJECT),
        e("PROJECT_DELETE", "Delete projects", PermissionScope.PROJECT),
        e("PROJECT_ROLE_MANAGE", "Assign roles to members of this project", PermissionScope.PROJECT),
        e("ISSUE_READ", "Read tasks", PermissionScope.PROJECT),
        e("ISSUE_CREATE", "Create tasks", PermissionScope.PROJECT),
        e("ISSUE_UPDATE", "Update tasks within assigned ownership rules", PermissionScope.PROJECT),
        e("ISSUE_UPDATE_ALL", "Update any task in this scope", PermissionScope.PROJECT),
        e("ISSUE_UPDATE_ASSIGNED", "Update tasks assigned to the current user", PermissionScope.PROJECT),
        e("ISSUE_ASSIGN_DEVELOPER", "Assign task developers", PermissionScope.PROJECT),
        e("ISSUE_STATUS_MANAGE", "Set permitted manager-level task statuses", PermissionScope.PROJECT),
        e("ISSUE_STATUS_ADVANCE", "Advance assigned tasks through developer states", PermissionScope.PROJECT),
        e("ISSUE_APPROVE_TEAM_LEAD", "Approve or reject tested tasks at Team Lead review", PermissionScope.PROJECT),
        e("ISSUE_APPROVE_PROJECT_MANAGER", "Approve or reject Team Lead-approved tasks", PermissionScope.PROJECT),
        e("ISSUE_DELETE", "Delete tasks", PermissionScope.PROJECT),
        e("TESTER_ASSIGN", "Assign testers to tasks", PermissionScope.PROJECT),
        e("ISSUE_DEPENDENCY_MANAGE", "Create and remove task dependencies", PermissionScope.PROJECT),
        e("COMMENT_READ", "Read task comments", PermissionScope.PROJECT),
        e("COMMENT_CREATE", "Create task comments", PermissionScope.PROJECT),
        e("COMMENT_DELETE", "Delete comments", PermissionScope.PROJECT),
        e("REPORT_READ", "Read project and sprint reports", PermissionScope.PROJECT),
        e("DASHBOARD_READ", "Read project dashboard metrics", PermissionScope.PROJECT),
        e("AUDIT_READ", "Read project audit history", PermissionScope.PROJECT),
        e("AUTOMATION_MANAGE", "Manage project workflow automation", PermissionScope.PROJECT),
        e("TESTING_QUEUE_READ", "Read the testing queue", PermissionScope.PROJECT),
        e("TESTING_MY_READ", "Read assigned testing work", PermissionScope.PROJECT),
        e("TESTING_HISTORY_READ", "Read task testing history", PermissionScope.PROJECT),
        e("TEST_EXECUTE", "Start and complete assigned testing", PermissionScope.PROJECT),
        e("TEAM_READ", "Read team details", PermissionScope.PROJECT_TEAM),
        e("TEAM_MANAGE", "Manage team membership", PermissionScope.PROJECT_TEAM),
        e("TEAM_ACTIVITY_READ", "Read team activity", PermissionScope.PROJECT_TEAM),
        e("TEAM_ROLE_MANAGE", "Assign roles to existing team members", PermissionScope.PROJECT_TEAM)
    );

    private PermissionCatalogue() {}
    private static Entry e(String key, String description, PermissionScope scope) {
        return new Entry(key, description, scope);
    }
    public static List<Entry> entries() { return ENTRIES; }
    /** Permissions that may be inspected and configured through ordinary role management. */
    public static List<Entry> editableEntries() {
        return ENTRIES.stream().filter(entry -> !"ROLE_MANAGE".equals(entry.key())).toList();
    }
    public static List<PermissionDefinition> definitions() {
        return ENTRIES.stream().map(e -> new PermissionDefinition(e.key(), e.description(), e.scope())).toList();
    }
    public static Entry find(String key) {
        return ENTRIES.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
    }
}
