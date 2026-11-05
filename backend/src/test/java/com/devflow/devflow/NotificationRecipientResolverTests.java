package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.PermissionDefinition;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.service.NotificationRecipientResolver;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientResolverTests {
    @Mock private ProjectRoleAssignmentRepository projectRoles;
    @Mock private TeamRoleAssignmentRepository teamRoles;

    @Test
    void projectRecipientsIncludeCurrentTeamMembersAndScopedProjectAssigneesWithoutGlobalRoleBroadcast() {
        NotificationRecipientResolver resolver = new NotificationRecipientResolver(projectRoles, teamRoles);
        User member = user(1L, "member@example.test");
        User disabled = user(2L, "disabled@example.test"); disabled.setEnabled(false);
        Team team = new Team(); team.setId(4L); team.setManager(member); team.setMembers(new LinkedHashSet<>(List.of(member, disabled)));
        Project project = new Project(); project.setId(3L); project.setTeam(team);
        when(projectRoles.findByProjectId(3L)).thenReturn(List.of());

        assertEquals(List.of(member), resolver.projectParticipants(project).stream().toList());
    }

    @Test
    void taskRecipientsDeduplicateAssigneeTesterReporterAndOnlyScopedApprovalResponsibilities() {
        NotificationRecipientResolver resolver = new NotificationRecipientResolver(projectRoles, teamRoles);
        User assignee = user(1L, "developer@example.test");
        User tester = user(2L, "tester@example.test");
        User lead = user(3L, "lead@example.test");
        User unrelated = user(4L, "unrelated@example.test");
        Project project = new Project(); project.setId(9L);
        Issue issue = new Issue(); issue.setProject(project); issue.setAssignedUser(assignee); issue.setTester(tester); issue.setReportedBy(assignee);
        AuthorizationRole leadRole = new AuthorizationRole("TEAM_LEAD_PROJECT", "Lead", PermissionScope.PROJECT, false);
        leadRole.setPermissions(Set.of(new PermissionDefinition("ISSUE_APPROVE_TEAM_LEAD", "Approve", PermissionScope.PROJECT)));
        AuthorizationRole viewerRole = new AuthorizationRole("VIEWER_PROJECT", "Viewer", PermissionScope.PROJECT, false);
        viewerRole.setPermissions(Set.of(new PermissionDefinition("ISSUE_READ", "Read", PermissionScope.PROJECT)));
        when(projectRoles.findByProjectId(9L)).thenReturn(List.of(
            new ProjectRoleAssignment(project, lead, leadRole), new ProjectRoleAssignment(project, unrelated, viewerRole)));

        assertEquals(Set.of(assignee, tester, lead), Set.copyOf(resolver.issueParticipants(issue)));
    }

    private User user(Long id, String email) {
        User user = new User(); user.setId(id); user.setEmail(email); return user;
    }
}
