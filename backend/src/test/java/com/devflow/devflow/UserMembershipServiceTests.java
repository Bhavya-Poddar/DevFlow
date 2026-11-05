package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.service.UserMembershipService;

class UserMembershipServiceTests {
    @Test
    void returnsScopedRolesAndTeamMembershipsForAuthorizedAdmin() {
        UserRepository users = mock(UserRepository.class);
        TeamRepository teams = mock(TeamRepository.class);
        ProjectRoleAssignmentRepository projectRoles = mock(ProjectRoleAssignmentRepository.class);
        TeamRoleAssignmentRepository teamRoles = mock(TeamRoleAssignmentRepository.class);
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        User target = new User(); target.setId(8L); target.setName("Dev");
        when(users.findById(8L)).thenReturn(Optional.of(target));
        Project project = new Project(); project.setId(12L); project.setName("Atlas");
        AuthorizationRole projectRole = new AuthorizationRole("DEVELOPER_PROJECT", "Developer (Project)", PermissionScope.PROJECT, true);
        org.springframework.test.util.ReflectionTestUtils.setField(projectRole, "id", 21L);
        when(projectRoles.findByUserId(8L)).thenReturn(List.of(new ProjectRoleAssignment(project, target, projectRole)));
        Team team = new Team(); team.setId(15L); team.setName("Platform"); team.setManager(target); team.setMembers(Set.of(target));
        when(teams.findAllDetailed()).thenReturn(List.of(team));
        AuthorizationRole teamRole = new AuthorizationRole("TEAM_LEAD_TEAM", "Team Lead", PermissionScope.PROJECT_TEAM, true);
        org.springframework.test.util.ReflectionTestUtils.setField(teamRole, "id", 22L);
        when(teamRoles.findByUserId(8L)).thenReturn(List.of(new TeamRoleAssignment(team, target, teamRole)));

        var result = new UserMembershipService(users, teams, projectRoles, teamRoles, permissions)
            .getMemberships(8L, "admin@devflow.test");

        assertEquals(1, result.projects().size());
        assertEquals("Atlas", result.projects().getFirst().projectName());
        assertEquals("Developer (Project)", result.projects().getFirst().roleName());
        assertEquals(1, result.teams().size());
        assertEquals("Platform", result.teams().getFirst().teamName());
        assertEquals("Team Lead", result.teams().getFirst().roleName());
        assertEquals(true, result.teams().getFirst().manager());
        verify(permissions).require("admin@devflow.test", "USER_MANAGE", null, null);
    }

    @Test
    void deniesMembershipDisclosureBeforeLoadingTargetWhenActorLacksPermission() {
        UserRepository users = mock(UserRepository.class);
        TeamRepository teams = mock(TeamRepository.class);
        ProjectRoleAssignmentRepository projectRoles = mock(ProjectRoleAssignmentRepository.class);
        TeamRoleAssignmentRepository teamRoles = mock(TeamRoleAssignmentRepository.class);
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
            .when(permissions).require("viewer@devflow.test", "USER_MANAGE", null, null);

        assertThrows(ResponseStatusException.class, () -> new UserMembershipService(users, teams,
            projectRoles, teamRoles, permissions).getMemberships(8L, "viewer@devflow.test"));

        verifyNoInteractions(users, teams, projectRoles, teamRoles);
    }
}
