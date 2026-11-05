package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.devflow.devflow.dto.UserUpdateRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.UserService;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.service.AdminRecoveryCapability;
import static org.mockito.Mockito.doNothing;

class UserServiceAuditTests {

    @Test
    void selfEmailUpdateKeepsAuthenticatedActorAndAuditsOnlyChangedFieldNames() {
        UserRepository users = mock(UserRepository.class);
        AuditLogServices audit = mock(AuditLogServices.class);
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        AdminRecoveryCapability recoveryCapability = mock(AdminRecoveryCapability.class);
        doNothing().when(permissions).require("old@example.test", "USER_MANAGE", null, null);
        User admin = new User();
        admin.setId(15L);
        admin.setName("Administrator");
        admin.setEmail("old@example.test");
        admin.setRole("ADMIN");
        when(recoveryCapability.hasCapability(admin)).thenReturn(false);
        when(users.findByEmailIgnoreCase("old@example.test")).thenReturn(Optional.of(admin));
        when(users.findById(15L)).thenReturn(Optional.of(admin));
        when(users.saveAndFlush(admin)).thenReturn(admin);

        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        ReflectionTestUtils.setField(service, "effectivePermissionService", permissions);
        ReflectionTestUtils.setField(service, "adminRecoveryCapability", recoveryCapability);
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("new@example.test");

        User updated = service.updateUser(15L, request, "old@example.test");

        assertEquals("new@example.test", updated.getEmail());
        verify(audit).createLog("USER_UPDATED",
                "targetType=USER; targetId=15; updatedFields=email", null, 15L);
        verify(users).findByEmailIgnoreCase("old@example.test");
        verify(users, never()).findByEmailIgnoreCase("new@example.test");
    }
}
