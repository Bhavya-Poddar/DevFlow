package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.RoleManagementService;
import com.devflow.devflow.service.UserService;

/** Compatibility coverage for the legacy user-role API, which now delegates to dynamic role assignments. */
@ExtendWith(MockitoExtension.class)
class UserRoleManagementTests {
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationRoleRepository authorizationRoleRepository;
    @Mock private RoleManagementService roleManagementService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogServices auditLogService;
    @InjectMocks private UserService userService;

    @Test
    void legacyRoleUpdateNormalizesAndDelegatesAConfiguredGlobalRole() {
        User target = user(2L, "target@example.com", "VIEWER");
        when(authorizationRoleRepository.findByCodeAndRetiredFalse("PROJECT_MANAGER"))
            .thenReturn(Optional.of(role("PROJECT_MANAGER", PermissionScope.GLOBAL, 7L)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        User result = userService.updateUserRole(2L, " project_manager ", "admin@example.com");

        assertEquals(target, result);
        verify(roleManagementService).assignGlobalRole(2L, 7L, "admin@example.com");
    }

    @Test
    void legacyRoleUpdateRejectsUnknownOrRetiredRolesWithoutChangingAccount() {
        when(authorizationRoleRepository.findByCodeAndRetiredFalse("ROOT")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> userService.updateUserRole(2L, " root ", "admin@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(roleManagementService, never()).assignGlobalRole(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void legacyRoleUpdateRejectsScopedRoles() {
        when(authorizationRoleRepository.findByCodeAndRetiredFalse("DEVELOPER_PROJECT"))
            .thenReturn(Optional.of(role("DEVELOPER_PROJECT", PermissionScope.PROJECT, 8L)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> userService.updateUserRole(2L, "DEVELOPER_PROJECT", "admin@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(roleManagementService, never()).assignGlobalRole(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setName(email);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private AuthorizationRole role(String code, PermissionScope scope, Long id) {
        AuthorizationRole role = new AuthorizationRole(code, code, scope, true);
        ReflectionTestUtils.setField(role, "id", id);
        return role;
    }
}
