package com.devflow.devflow.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.service.ProjectAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Enforces project membership on resource-ID routes before controller serialization or mutation. */
@Component
public class ProjectPrivacyInterceptor implements HandlerInterceptor {
    private static final Pattern ID = Pattern.compile("^\\d+$");
    private final ProjectAccessService access;
    private final CommentRepository commentRepository;
    private final AuditLogsRepository auditLogsRepository;

    public ProjectPrivacyInterceptor(ProjectAccessService access, CommentRepository commentRepository,
            AuditLogsRepository auditLogsRepository) {
        this.access = access;
        this.commentRepository = commentRepository;
        this.auditLogsRepository = auditLogsRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String email = authenticatedEmail();

        if (path.matches("^/api/projects/\\d+(?:/.*)?$")) {
            access.requireProjectAccess(firstId(path, 3), email);
        } else if (path.matches("^/api/issues/\\d+(?:/.*)?$")) {
            access.requireIssueAccess(firstId(path, 3), email);
            if (path.matches("^/api/issues/\\d+/dependencies/\\d+$")) {
                access.requireIssueAccess(Long.valueOf(path.substring(path.lastIndexOf('/') + 1)), email);
            }
        } else if (path.matches("^/api/sprints/\\d+(?:/.*)?$")) {
            access.requireSprintAccess(firstId(path, 3), email);
        } else if (path.matches("^/api/testing/\\d+/(?:start|complete)$")) {
            access.requireIssueAccess(firstId(path, 3), email);
        } else if (path.matches("^/api/testing/issue/\\d+/history$")) {
            access.requireIssueAccess(firstId(path, 4), email);
        } else if (path.matches("^/api/reports/projects/\\d+$")) {
            access.requireProjectAccess(firstId(path, 4), email);
        } else if (path.matches("^/api/reports/sprints/\\d+/burndown$")) {
            access.requireSprintAccess(firstId(path, 4), email);
        } else if (path.matches("^/api/comments/issue/\\d+$")) {
            access.requireIssueAccess(firstId(path, 4), email);
        } else if (path.matches("^/api/comments/\\d+$")) {
            Long issueId = commentRepository.findById(firstId(path, 3))
                .map(comment -> comment.getIssue() == null ? null : comment.getIssue().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
            if (issueId != null) access.requireIssueAccess(issueId, email);
        } else if (path.equals("/api/audit-logs") || path.matches("^/api/audit-logs/.*$")) {
            String issueValue = request.getParameter("issueId");
            if (path.matches("^/api/audit-logs/issue/\\d+$")) issueValue = path.substring("/api/audit-logs/issue/".length());
            if (issueValue != null && ID.matcher(issueValue).matches()) {
                access.requireIssueAccess(Long.valueOf(issueValue), email);
            } else if (!access.hasGlobalPermission(email, "AUDIT_READ_ALL")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Project managers must select an accessible task to view its audit history");
            }
        } else if (path.startsWith("/api/dashboard/")) {
            String projectValue = request.getParameter("projectId");
            String sprintValue = request.getParameter("sprintId");
            if (projectValue != null && ID.matcher(projectValue).matches()) {
                access.requireProjectAccess(Long.valueOf(projectValue), email);
            }
            if (sprintValue != null && ID.matcher(sprintValue).matches()) {
                access.requireSprintAccess(Long.valueOf(sprintValue), email);
            }
            if (projectValue == null && sprintValue == null
                    && !access.hasGlobalPermission(email, "DASHBOARD_READ_ALL")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select an accessible project or sprint to view dashboard metrics");
            }
        }
        return true;
    }

    private String authenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return authentication.getName();
    }

    private Long firstId(String path, int segment) {
        String[] parts = path.split("/");
        return Long.valueOf(parts[segment]);
    }
}
