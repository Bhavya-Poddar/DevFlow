package com.devflow.devflow.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.security.ProjectPrivacyInterceptor;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RequestPermissionService {
    private static final Pattern PROJECT = Pattern.compile("^/api/projects/(\\d+)(?:/.*)?$");
    private static final Pattern ISSUE = Pattern.compile("^/api/issues/(\\d+)(?:/.*)?$");
    private static final Pattern SPRINT = Pattern.compile("^/api/sprints/(\\d+)(?:/.*)?$");
    private static final Pattern TEST_PATH = Pattern.compile("^/api/testing/(\\d+)/(?:start|complete)$");
    private static final Pattern TEST_HISTORY = Pattern.compile("^/api/testing/issue/(\\d+)/history$");
    private static final Pattern REPORT_PROJECT = Pattern.compile("^/api/reports/projects/(\\d+)$");
    private static final Pattern REPORT_SPRINT = Pattern.compile("^/api/reports/sprints/(\\d+)/burndown$");
    private static final Pattern COMMENT_ISSUE = Pattern.compile("^/api/comments/issue/(\\d+)$");
    private static final Pattern TEAM = Pattern.compile("^/api/teams/(\\d+)(?:/.*)?$");
    private static final Pattern AUDIT_ISSUE = Pattern.compile("^/api/audit-logs/issue/(\\d+)$");
    private static final Pattern USER_ID = Pattern.compile("^/api/users/(\\d+)$");
    private static final Pattern USER_MEMBERSHIPS = Pattern.compile("^/api/users/(\\d+)/memberships$");
    private static final Pattern USER_ROLE = Pattern.compile("^/api/users/(\\d+)/role$");
    private static final Pattern USER_AVATAR = Pattern.compile("^/api/users/(\\d+)/avatar$");
    private static final Pattern PROJECT_ID = Pattern.compile("^/api/projects/(\\d+)$");
    private static final Pattern PROJECT_ROLE = Pattern.compile("^/api/projects/(\\d+)/members/(\\d+)/role$");
    private static final Pattern SPRINT_ID = Pattern.compile("^/api/sprints/(\\d+)$");
    private static final Pattern ISSUE_ID = Pattern.compile("^/api/issues/(\\d+)$");
    private static final Pattern ISSUE_ACTIVITY = Pattern.compile("^/api/issues/(\\d+)/(?:activity|timeline)$");
    private static final Pattern ISSUE_TESTER = Pattern.compile("^/api/issues/(\\d+)/tester$");
    private static final Pattern ISSUE_DEPENDENCIES = Pattern.compile("^/api/issues/(\\d+)/dependencies(?:/(\\d+))?$");
    private static final Pattern ISSUE_APPROVALS = Pattern.compile("^/api/issues/(\\d+)/approvals(?:/([A-Za-z_-]+))?$");
    private static final Pattern ISSUE_MAINTENANCE = Pattern.compile("^/api/issues/(\\d+)/maintenance(?:-follow-ups)?$");
    private static final Pattern TEAM_ID = Pattern.compile("^/api/teams/(\\d+)$");
    private static final Pattern TEAM_MEMBERS = Pattern.compile("^/api/teams/(\\d+)/members(?:/(\\d+))?$");
    private static final Pattern TEAM_ROLE = Pattern.compile("^/api/teams/(\\d+)/members/(\\d+)/role$");
    private static final Pattern TEAM_ACTIVITY = Pattern.compile("^/api/teams/(\\d+)/activity$");
    private static final Pattern COMMENT_ID = Pattern.compile("^/api/comments/(\\d+)$");
    private static final Pattern COMMENT_ISSUE_EXACT = Pattern.compile("^/api/comments/issue/(\\d+)$");

    private final EffectivePermissionService permissions;
    private final IssueRepository issues;
    private final SprintRepository sprints;
    private final CommentRepository comments;

    public RequestPermissionService(EffectivePermissionService permissions, IssueRepository issues,
            SprintRepository sprints, CommentRepository comments) {
        this.permissions = permissions; this.issues = issues; this.sprints = sprints; this.comments = comments;
    }

    @Transactional(readOnly = true)
    public boolean isAllowed(HttpServletRequest request, String email) {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String key = permissionFor(method, path);
        if (key == null) return !path.startsWith("/api/") && !path.startsWith("/actuator/"); // Unknown API/probe routes fail closed.
        if ("AUTHENTICATED".equals(key)) return true;

        Long teamId = captured(TEAM, path);
        Long projectId = projectId(path, request);
        if ("AUDIT_READ".equals(key) && projectId == null
                && permissions.hasGlobal(email, "AUDIT_READ_ALL")) return true;
        if ("DASHBOARD_READ".equals(key) && projectId == null
                && permissions.hasGlobal(email, "DASHBOARD_READ_ALL")) return true;
        if (teamId != null) return permissions.has(email, key, null, teamId);
        if (projectId != null) return permissions.has(email, key, projectId, null);
        return permissions.hasAnywhere(email, key);
    }

    private String permissionFor(String method, String path) {
        if ("GET".equals(method) && "/api/approvals/pending".equals(path)) return "AUTHENTICATED";
        if (Set.of("/api/config/menu", "/api/config/website", "/api/config/statuses").contains(path)
                && "GET".equals(method)) return "AUTHENTICATED";
        if ("GET".equals(method) && path.matches("^/api/config/dropdowns/[A-Za-z0-9_-]+$")) return "AUTHENTICATED";
        if (path.startsWith("/api/admin/config/")) return "ROLE_MANAGE";
        if ("GET".equals(method) && "/api/admin/roles".equals(path)) return "ROLE_MANAGE";
        if ("GET".equals(method) && "/api/admin/roles/permissions".equals(path)) return "ROLE_MANAGE";
        if ("POST".equals(method) && "/api/admin/roles".equals(path)) return "ROLE_MANAGE";
        if (("PUT".equals(method) || "DELETE".equals(method)) && path.matches("^/api/admin/roles/\\d+$")) return "ROLE_MANAGE";
        if ("PUT".equals(method) && path.matches("^/api/admin/recovery/accounts/\\d+$")) return "AUTHENTICATED";
        if (("GET".equals(method) && ("/api/users/me".equals(path) || "/api/users/me/permissions".equals(path)))
                || ("PUT".equals(method) && path.matches("^/api/users/me/(?:profile|password)$"))
                || ("POST".equals(method) && "/api/users/me/avatar".equals(path))
                || ("GET".equals(method) && USER_AVATAR.matcher(path).matches())) return "AUTHENTICATED";
        if ("GET".equals(method) && ("/api/users/assignable".equals(path) || "/api/users/testers".equals(path))) return "AUTHENTICATED";
        if ("GET".equals(method) && "/api/notifications".equals(path)) return "AUTHENTICATED";
        if ("GET".equals(method) && Set.of("/api/notifications/unread", "/api/notifications/unread/count", "/api/notifications/page").contains(path)) return "AUTHENTICATED";
        if ("PUT".equals(method) && (path.matches("^/api/notifications/\\d+/read$") || "/api/notifications/read-all".equals(path))) return "AUTHENTICATED";
        if ("GET".equals(method) && path.equals("/api/users")) return "USER_READ";
        if ("GET".equals(method) && USER_MEMBERSHIPS.matcher(path).matches()) return "USER_MANAGE";
        if ("POST".equals(method) && path.equals("/api/users")) return "USER_MANAGE";
        if (USER_ROLE.matcher(path).matches()) return "PUT".equals(method) ? "ROLE_MANAGE" : null;
        if (USER_ID.matcher(path).matches()) {
            if ("GET".equals(method)) return "USER_READ";
            if ("PUT".equals(method) || "DELETE".equals(method)) return "USER_MANAGE";
            return null;
        }
        if ("GET".equals(method) && path.matches("^/api/users/email/[^/]+$")) return "USER_READ";
        if ("GET".equals(method) && "/api/audit-logs".equals(path)) return "AUDIT_READ";
        if ("GET".equals(method) && AUDIT_ISSUE.matcher(path).matches()) return "AUDIT_READ";
        if ("DELETE".equals(method) && path.matches("^/api/audit-logs/\\d+$")) return "AUDIT_DELETE";
        if ("GET".equals(method) && REPORT_PROJECT.matcher(path).matches()) return "REPORT_READ";
        if ("GET".equals(method) && REPORT_SPRINT.matcher(path).matches()) return "REPORT_READ";
        if ("GET".equals(method) && Set.of("/api/dashboard/summary", "/api/dashboard/overview",
                "/api/dashboard/project-progress", "/api/dashboard/workload", "/api/dashboard/testing",
                "/api/dashboard/bottlenecks", "/api/dashboard/sprints", "/api/dashboard/workload-risk").contains(path)) return "DASHBOARD_READ";
        if (("GET".equals(method) && "/api/automations/rules".equals(path))
                || ("POST".equals(method) && "/api/automations/rules".equals(path))
                || (("PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))
                    && path.matches("^/api/automations/rules/\\d+(?:/enabled)?$"))
                || ("GET".equals(method) && "/api/automations/executions".equals(path))) return "AUTOMATION_MANAGE";
        if ("GET".equals(method) && "/api/teams".equals(path)) return "AUTHENTICATED";
        if ("POST".equals(method) && "/api/teams".equals(path)) return "TEAM_CREATE";
        if ("GET".equals(method) && TEAM_ID.matcher(path).matches()) return "TEAM_READ";
        if ("GET".equals(method) && TEAM_ACTIVITY.matcher(path).matches()) return "TEAM_ACTIVITY_READ";
        if (TEAM_ROLE.matcher(path).matches()) return Set.of("PUT", "DELETE").contains(method) ? "TEAM_ROLE_MANAGE" : null;
        if (TEAM_MEMBERS.matcher(path).matches()) {
            if ("POST".equals(method) && path.endsWith("/members")) return "TEAM_MANAGE";
            if ("DELETE".equals(method) && path.matches("^/api/teams/\\d+/members/\\d+$")) return "TEAM_MANAGE";
            return null;
        }
        if ("GET".equals(method) && "/api/testing/queue".equals(path)) return "TESTING_QUEUE_READ";
        if ("GET".equals(method) && "/api/testing/my".equals(path)) return "TESTING_MY_READ";
        if (TEST_HISTORY.matcher(path).matches() && "GET".equals(method)) return "TESTING_HISTORY_READ";
        if (TEST_PATH.matcher(path).matches() && Set.of("POST").contains(method)) return "TEST_EXECUTE";
        if ("POST".equals(method) && "/api/projects".equals(path)) return "PROJECT_CREATE";
        if ("GET".equals(method) && "/api/projects".equals(path)) return "PROJECT_READ";
        if (PROJECT_ROLE.matcher(path).matches()) return Set.of("PUT", "DELETE").contains(method) ? "PROJECT_ROLE_MANAGE" : null;
        if (PROJECT_ID.matcher(path).matches()) {
            if ("GET".equals(method)) return "PROJECT_READ";
            if ("PUT".equals(method) || "PATCH".equals(method)) return "PROJECT_UPDATE";
            if ("DELETE".equals(method)) return "PROJECT_DELETE";
            return null;
        }
        if ("GET".equals(method) && path.matches("^/api/projects/\\d+/assignable-members$")) return "PROJECT_READ";
        if ("GET".equals(method) && "/api/sprints".equals(path)) return "PROJECT_READ";
        if ("POST".equals(method) && "/api/sprints".equals(path)) return "PROJECT_UPDATE";
        if (SPRINT_ID.matcher(path).matches()) {
            if ("GET".equals(method)) return "PROJECT_READ";
            if ("PUT".equals(method) || "PATCH".equals(method)) return "PROJECT_UPDATE";
            if ("DELETE".equals(method)) return "PROJECT_DELETE";
            return null;
        }
        if ("GET".equals(method) && ("/api/issues".equals(path) || ISSUE_ID.matcher(path).matches())) return "ISSUE_READ";
        if ("GET".equals(method) && ISSUE_ACTIVITY.matcher(path).matches()) return "ISSUE_READ";
        if (ISSUE_TESTER.matcher(path).matches()) return "PUT".equals(method) ? "TESTER_ASSIGN" : null;
        if (ISSUE_DEPENDENCIES.matcher(path).matches()) {
            if ("POST".equals(method) && !path.matches(".*/dependencies/\\d+$")) return "ISSUE_DEPENDENCY_MANAGE";
            if ("DELETE".equals(method) && path.matches(".*/dependencies/\\d+$")) return "ISSUE_DEPENDENCY_MANAGE";
            return null;
        }
        Matcher approval = ISSUE_APPROVALS.matcher(path);
        if (approval.matches()) {
            if ("GET".equals(method) && approval.group(2) == null) return "ISSUE_READ";
            if ("POST".equals(method) && approval.group(2) != null) {
                return "TEAM_LEAD".equalsIgnoreCase(approval.group(2).replace('-', '_'))
                    ? "ISSUE_APPROVE_TEAM_LEAD" : "PROJECT_MANAGER".equalsIgnoreCase(approval.group(2).replace('-', '_'))
                    ? "ISSUE_APPROVE_PROJECT_MANAGER" : null;
            }
            return null;
        }
        if (ISSUE_MAINTENANCE.matcher(path).matches()) return "GET".equals(method) ? "ISSUE_READ"
            : "POST".equals(method) && path.endsWith("/maintenance-follow-ups") ? "ISSUE_CREATE" : null;
        if ("POST".equals(method) && "/api/issues".equals(path)) return "ISSUE_CREATE";
        if (ISSUE_ID.matcher(path).matches()) {
            if ("PUT".equals(method) || "PATCH".equals(method)) return "ISSUE_UPDATE";
            if ("DELETE".equals(method)) return "ISSUE_DELETE";
            return null;
        }
        if ("POST".equals(method) && "/api/comments".equals(path)) return "COMMENT_CREATE";
        if ("GET".equals(method) && COMMENT_ISSUE_EXACT.matcher(path).matches()) return "COMMENT_READ";
        if ("DELETE".equals(method) && COMMENT_ID.matcher(path).matches()) return "COMMENT_DELETE";
        return null;
    }

    private Long projectId(String path, HttpServletRequest request) {
        Long id = captured(PROJECT, path);
        if (id != null) return id;
        id = captured(REPORT_PROJECT, path);
        if (id != null) return id;
        id = captured(REPORT_SPRINT, path);
        if (id != null) return sprints.findById(id).map(s -> s.getProject() == null ? null : s.getProject().getId()).orElse(null);
        id = captured(TEST_PATH, path);
        if (id != null) return issueProject(id);
        id = captured(TEST_HISTORY, path);
        if (id != null) return issueProject(id);
        id = captured(COMMENT_ISSUE, path);
        if (id != null) return issueProject(id);
        id = captured(ISSUE, path);
        if (id != null) return issueProject(id);
        id = captured(AUDIT_ISSUE, path);
        if (id != null) return issueProject(id);
        id = captured(SPRINT, path);
        if (id != null) return sprints.findById(id).map(s -> s.getProject() == null ? null : s.getProject().getId()).orElse(null);
        if (path.matches("^/api/comments/\\d+$")) {
            long commentId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            return comments.findById(commentId).map(c -> c.getIssue() == null || c.getIssue().getProject() == null
                ? null : c.getIssue().getProject().getId()).orElse(null);
        }
        String projectFilter = request.getParameter("projectId");
        if (projectFilter != null && projectFilter.matches("\\d+")) return Long.valueOf(projectFilter);
        String sprintFilter = request.getParameter("sprintId");
        if (sprintFilter != null && sprintFilter.matches("\\d+")) {
            long sprintId = Long.parseLong(sprintFilter);
            return sprints.findById(sprintId).map(s -> s.getProject() == null ? null : s.getProject().getId()).orElse(null);
        }
        String issueFilter = request.getParameter("issueId");
        if (issueFilter != null && issueFilter.matches("\\d+")) return issueProject(Long.valueOf(issueFilter));
        return null;
    }

    private Long issueProject(Long issueId) {
        return issues.findById(issueId).map(i -> i.getProject() == null ? null : i.getProject().getId()).orElse(null);
    }

    private Long captured(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }
}
