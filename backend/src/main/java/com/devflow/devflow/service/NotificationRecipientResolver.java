package com.devflow.devflow.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;

/** Resolves recipients from current project/team responsibilities, never from global role names. */
@Component
public class NotificationRecipientResolver {
    private final ProjectRoleAssignmentRepository projectRoles;
    private final TeamRoleAssignmentRepository teamRoles;

    public NotificationRecipientResolver(ProjectRoleAssignmentRepository projectRoles,
            TeamRoleAssignmentRepository teamRoles) {
        this.projectRoles = projectRoles;
        this.teamRoles = teamRoles;
    }

    public Collection<User> projectParticipants(Project project) {
        Map<Long, User> users = new LinkedHashMap<>();
        if (project == null || project.getId() == null) return users.values();
        projectRoles.findByProjectId(project.getId()).forEach(assignment -> add(users, assignment.getUser()));
        if (project.getTeam() != null) {
            add(users, project.getTeam().getManager());
            if (project.getTeam().getMembers() != null) project.getTeam().getMembers().forEach(user -> add(users, user));
        }
        return users.values();
    }

    public Collection<User> teamParticipants(Team team) {
        Map<Long, User> users = new LinkedHashMap<>();
        if (team == null || team.getId() == null) return users.values();
        add(users, team.getManager());
        if (team.getMembers() != null) team.getMembers().forEach(user -> add(users, user));
        teamRoles.findByTeamId(team.getId()).forEach(assignment -> add(users, assignment.getUser()));
        return users.values();
    }

    /** Direct task participants plus scoped approvers responsible for the task's project. */
    public Collection<User> issueParticipants(Issue issue) {
        Map<Long, User> users = new LinkedHashMap<>();
        if (issue == null) return users.values();
        add(users, issue.getAssignedUser());
        add(users, issue.getTester());
        add(users, issue.getReportedBy());
        Project project = issue.getProject();
        if (project == null || project.getId() == null) return users.values();
        projectRoles.findByProjectId(project.getId()).stream()
            .filter(assignment -> hasApprovalResponsibility(assignment, "ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"))
            .forEach(assignment -> add(users, assignment.getUser()));
        Team team = project.getTeam();
        if (team != null && team.getId() != null) {
            teamRoles.findByTeamId(team.getId()).stream()
                .filter(assignment -> hasApprovalResponsibility(assignment, "ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"))
                .forEach(assignment -> add(users, assignment.getUser()));
        }
        return users.values();
    }

    private boolean hasApprovalResponsibility(ProjectRoleAssignment assignment, String... permissions) {
        return assignment != null && assignment.getRole() != null && assignment.getRole().getPermissions().stream()
            .anyMatch(permission -> java.util.Arrays.asList(permissions).contains(permission.getKey()));
    }

    private boolean hasApprovalResponsibility(TeamRoleAssignment assignment, String... permissions) {
        return assignment != null && assignment.getRole() != null && assignment.getRole().getPermissions().stream()
            .anyMatch(permission -> java.util.Arrays.asList(permissions).contains(permission.getKey()));
    }

    private void add(Map<Long, User> users, User user) {
        if (user != null && user.getId() != null && user.isEnabled()) users.putIfAbsent(user.getId(), user);
    }
}
