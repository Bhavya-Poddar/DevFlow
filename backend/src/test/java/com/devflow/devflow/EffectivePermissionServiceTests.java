package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionDefinition;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.service.AdminRecoveryCapability;

@ExtendWith(MockitoExtension.class)
class EffectivePermissionServiceTests {
    @Mock private UserRepository users;
    @Mock private AuthorizationRoleRepository roles;
    @Mock private ProjectRoleAssignmentRepository projectAssignments;
    @Mock private TeamRoleAssignmentRepository teamAssignments;
    @Mock private ProjectRepository projects;
    @Mock private TeamRepository teams;
    @Mock private AdminRecoveryCapability recoveryCapability;
    @InjectMocks private EffectivePermissionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(12L);
        user.setEmail("role-owner@example.test");
        user.setRole("CUSTOM_GLOBAL");
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        AuthorizationRole role = new AuthorizationRole("CUSTOM_GLOBAL", "Custom", PermissionScope.GLOBAL, false);
        role.setPermissions(new LinkedHashSet<>(Set.of(
            new PermissionDefinition("ROLE_MANAGE", "Manage roles", PermissionScope.GLOBAL),
            new PermissionDefinition("USER_READ", "Read users", PermissionScope.GLOBAL))));
        when(roles.findByCode("CUSTOM_GLOBAL")).thenReturn(Optional.of(role));
        org.mockito.Mockito.lenient().when(recoveryCapability.hasCapability(user)).thenReturn(false);
    }

    @Test
    void editableRoleGrantCannotConferProtectedRoleManagement() {
        assertFalse(service.has(user.getEmail(), "ROLE_MANAGE", null, null));
        assertFalse(service.globalPermissions(user.getEmail()).contains("ROLE_MANAGE"));
        assertTrue(service.has(user.getEmail(), "USER_READ", null, null));
    }

    @Test
    void separatelyProtectedRecoveryCapabilityGrantsRoleManagement() {
        when(recoveryCapability.hasCapability(user)).thenReturn(true);

        assertTrue(service.has(user.getEmail(), "ROLE_MANAGE", null, null));
        assertTrue(service.globalPermissions(user.getEmail()).contains("ROLE_MANAGE"));
    }

    @Test
    void configuredBootstrapRecoveryIsReportedByTheCurrentPermissionResponse() {
        when(recoveryCapability.hasCapability(user)).thenReturn(true);
        when(projectAssignments.findByUserId(user.getId())).thenReturn(java.util.List.of());
        when(teamAssignments.findByUserId(user.getId())).thenReturn(java.util.List.of());

        var response = service.effective(user.getEmail());

        assertTrue(response.recoveryCapable());
        assertTrue(response.global().contains("ROLE_MANAGE"));
    }

    @Test
    void projectGrantDoesNotSurviveRemovalFromTheProjectsPrivateTeam() {
        Project project = new Project();
        project.setId(42L);
        Team privateTeam = new Team();
        privateTeam.setId(91L);
        User manager = new User();
        manager.setId(99L);
        privateTeam.setManager(manager);
        project.setTeam(privateTeam);
        AuthorizationRole projectRole = new AuthorizationRole("READER_PROJECT", "Reader", PermissionScope.PROJECT, false);
        projectRole.setPermissions(new LinkedHashSet<>(Set.of(
            new PermissionDefinition("ISSUE_READ", "Read tasks", PermissionScope.PROJECT))));
        when(projects.findById(42L)).thenReturn(Optional.of(project));
        org.mockito.Mockito.lenient().when(projectAssignments.findByUserIdAndProjectId(user.getId(), 42L))
            .thenReturn(java.util.List.of(new ProjectRoleAssignment(project, user, projectRole)));
        assertFalse(service.has(user.getEmail(), "ISSUE_READ", 42L, null));
        assertFalse(service.permissionsAt(user.getEmail(), 42L, null).contains("ISSUE_READ"));
    }

    @Test
    void existingTeamMembershipRetainsReadOnlyActivityAccess() {
        Team team = new Team();
        team.setId(91L);
        team.setManager(new User());
        team.getMembers().add(user);
        when(teams.findDetailedById(91L)).thenReturn(Optional.of(team));

        assertTrue(service.has(user.getEmail(), "TEAM_ACTIVITY_READ", null, 91L));
    }
}
