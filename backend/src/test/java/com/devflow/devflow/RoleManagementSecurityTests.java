package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.RoleUpsertRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.PermissionDefinitionRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AdminRecoveryGuard;
import com.devflow.devflow.service.AdminRecoveryCapability;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.service.RoleManagementService;
import com.devflow.devflow.entity.PermissionScope;

@ExtendWith(MockitoExtension.class)
class RoleManagementSecurityTests {
    @Mock private AuthorizationRoleRepository roles;
    @Mock private PermissionDefinitionRepository definitions;
    @Mock private ProjectRoleAssignmentRepository projectAssignments;
    @Mock private TeamRoleAssignmentRepository teamAssignments;
    @Mock private ProjectRepository projects;
    @Mock private TeamRepository teams;
    @Mock private UserRepository users;
    @Mock private EffectivePermissionService permissions;
    @Mock private AdminRecoveryCapability recoveryCapability;
    @Mock private AdminRecoveryGuard recovery;
    @Mock private AuditLogServices audit;
    @InjectMocks private RoleManagementService service;

    @Test
    void ordinaryRoleConfigurationCannotGrantProtectedRoleManagement() {
        User actor = new User();
        actor.setId(5L);
        actor.setEmail("recovery@example.test");
        actor.setEnabled(true);
        actor.setAdminRecovery(true);
        when(roles.existsByCodeIgnoreCase("CUSTOM_ROLE")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(new RoleUpsertRequestDTO("CUSTOM_ROLE", "Custom role", PermissionScope.GLOBAL,
                Set.of("ROLE_MANAGE")), actor.getEmail()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void deletingRoleWithAssignmentsIsBlockedWithReassignmentGuidance() {
        User actor = recoveryAdmin();
        com.devflow.devflow.entity.AuthorizationRole role = new com.devflow.devflow.entity.AuthorizationRole(
            "CUSTOM_ROLE", "Custom", PermissionScope.GLOBAL, false);
        org.springframework.test.util.ReflectionTestUtils.setField(role, "id", 20L);
        when(roles.findByIdForUpdate(20L)).thenReturn(Optional.of(role));
        when(users.countByRole("CUSTOM_ROLE")).thenReturn(1L);
        when(projectAssignments.countByRole_Id(20L)).thenReturn(2L);
        when(teamAssignments.countByRole_Id(20L)).thenReturn(0L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.retire(20L, actor.getEmail()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("Reassign or remove"));
        org.mockito.Mockito.verify(roles, org.mockito.Mockito.never()).save(role);
    }

    @Test
    void protectedAdminRoleCannotBeDeletedEvenWhenUnassigned() {
        User actor = recoveryAdmin();
        com.devflow.devflow.entity.AuthorizationRole role = new com.devflow.devflow.entity.AuthorizationRole(
            "ADMIN", "Administrator", PermissionScope.GLOBAL, true);
        when(roles.findByIdForUpdate(1L)).thenReturn(Optional.of(role));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.retire(1L, actor.getEmail()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("protected ADMIN"));
        org.mockito.Mockito.verifyNoInteractions(users, projectAssignments, teamAssignments);
    }

    @Test
    void unassignedCustomRoleCanBeSafelyRetiredWithoutDeletingItsRecord() {
        User actor = recoveryAdmin();
        com.devflow.devflow.entity.AuthorizationRole role = new com.devflow.devflow.entity.AuthorizationRole(
            "CUSTOM_ROLE", "Custom", PermissionScope.GLOBAL, false);
        org.springframework.test.util.ReflectionTestUtils.setField(role, "id", 20L);
        when(roles.findByIdForUpdate(20L)).thenReturn(Optional.of(role));
        when(users.countByRole("CUSTOM_ROLE")).thenReturn(0L);
        when(projectAssignments.countByRole_Id(20L)).thenReturn(0L);
        when(teamAssignments.countByRole_Id(20L)).thenReturn(0L);
        when(permissions.requireEnabledUser(actor.getEmail())).thenReturn(actor);

        var result = service.retire(20L, actor.getEmail());

        org.junit.jupiter.api.Assertions.assertTrue(result.retired());
        org.mockito.Mockito.verify(roles).save(role);
    }

    private User recoveryAdmin() {
        User actor = new User();
        actor.setId(5L);
        actor.setEmail("recovery@example.test");
        actor.setEnabled(true);
        actor.setAdminRecovery(true);
        return actor;
    }
}
