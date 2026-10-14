package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserRoleManagementTests {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogServices auditLogService;

    @InjectMocks private UserService userService;

    @Test
    void adminCanPromoteViewerToDeveloper() {
        assertPromotesTo("DEVELOPER");
    }

    @Test
    void adminCanPromoteViewerToProjectManager() {
        assertPromotesTo("PROJECT_MANAGER");
    }

    @Test
    void adminCanPromoteViewerToAdmin() {
        assertPromotesTo("ADMIN");
    }

    @Test
    void adminCanPromoteViewerToTester() {
        assertPromotesTo("TESTER");
    }

    @Test
    void adminCanSetViewerRoleAndInputIsNormalized() {
        User actor = user(1L, "admin@example.com", "ADMIN");
        User target = user(2L, "target@example.com", "DEVELOPER");
        when(userRepository.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        User updated = userService.updateUserRole(2L, " viewer ", actor.getEmail());

        assertEquals("VIEWER", updated.getRole());
        verify(auditLogService).createLog(
            "ROLE_CHANGED", "User 'target@example.com' role changed from 'DEVELOPER' to 'VIEWER'", null, 1L);
    }

    @Test
    void rejectsRolesOutsideTheAllowedList() {
        for (String invalidRole : List.of("admin123", "SUPER_ADMIN", "ROOT", "")) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.updateUserRole(2L, invalidRole, "admin@example.com"));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        verify(userRepository, never()).findById(2L);
    }

    @Test
    void adminCannotChangeTheirOwnRole() {
        User admin = user(1L, "admin@example.com", "ADMIN");
        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> userService.updateUserRole(1L, "VIEWER", admin.getEmail()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(userRepository, never()).save(admin);
        verify(auditLogService, never()).createLog(eq("ROLE_CHANGED"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unchangedRoleDoesNotCreateAChangeAuditEntry() {
        User admin = user(1L, "admin@example.com", "ADMIN");
        User target = user(2L, "target@example.com", "VIEWER");
        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userService.updateUserRole(2L, "VIEWER", admin.getEmail());

        verify(userRepository, never()).save(target);
        verify(auditLogService, never()).createLog(eq("ROLE_CHANGED"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void assertPromotesTo(String role) {
        User admin = user(1L, "admin@example.com", "ADMIN");
        User target = user(2L, "target@example.com", "VIEWER");
        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        User updated = userService.updateUserRole(2L, role, admin.getEmail());

        assertEquals(role, updated.getRole());
        verify(auditLogService).createLog(
            "ROLE_CHANGED", "User 'target@example.com' role changed from 'VIEWER' to '" + role + "'", null, 1L);
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setName(email);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
