package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationInvariantLockRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AdminRecoveryCapability;
import com.devflow.devflow.service.AdminRecoveryGuard;

class AdminRecoveryCapabilityTests {

    @Test
    void configuredEnabledAdminHasRecoveryWithoutChangingItsStoredFlag() {
        AdminRecoveryCapability capability = new AdminRecoveryCapability("BOOTSTRAP@example.test");
        User user = user("bootstrap@example.test", "ADMIN", true, false);

        assertTrue(capability.hasCapability(user));
        assertTrue(capability.isConfiguredBootstrapAdmin(user));
        assertFalse(user.hasAdminRecovery());
    }

    @Test
    void editablePermissionsOrAnotherRoleCannotImpersonateConfiguredRecoveryIdentity() {
        AdminRecoveryCapability capability = new AdminRecoveryCapability("bootstrap@example.test");

        assertFalse(capability.hasCapability(user("bootstrap@example.test", "PROJECT_MANAGER", true, false)));
        assertFalse(capability.hasCapability(user("other@example.test", "ADMIN", true, false)));
        assertFalse(capability.hasCapability(user("bootstrap@example.test", "ADMIN", false, false)));
    }

    @Test
    void persistedRecoveryFlagRemainsIndependentOfRoleAndBootstrapConfiguration() {
        AdminRecoveryCapability capability = new AdminRecoveryCapability("");

        assertTrue(capability.hasCapability(user("recovery@example.test", "CUSTOM_ROLE", true, true)));
    }

    @Test
    void lastRecoveryGuardAcceptsOnlyTheConfiguredEnabledAdminWhenFlagWasNotBackfilled() {
        AdminRecoveryCapability capability = new AdminRecoveryCapability("bootstrap@example.test");
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        AuthorizationInvariantLockRepository locks = org.mockito.Mockito.mock(AuthorizationInvariantLockRepository.class);
        when(users.findEnabledRecoveryAccountsForUpdate()).thenReturn(List.of());
        when(users.findByEmailIgnoreCaseForUpdate("bootstrap@example.test"))
            .thenReturn(Optional.of(user("bootstrap@example.test", "ADMIN", true, false)));

        new AdminRecoveryGuard(locks, users, capability).requireRecoveryAccount();
    }

    @Test
    void lastRecoveryGuardRejectsDisabledOrRoleChangedConfiguredAccount() {
        AdminRecoveryCapability capability = new AdminRecoveryCapability("bootstrap@example.test");
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        AuthorizationInvariantLockRepository locks = org.mockito.Mockito.mock(AuthorizationInvariantLockRepository.class);
        when(users.findEnabledRecoveryAccountsForUpdate()).thenReturn(List.of());
        when(users.findByEmailIgnoreCaseForUpdate("bootstrap@example.test"))
            .thenReturn(Optional.of(user("bootstrap@example.test", "VIEWER", true, false)));

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
            () -> new AdminRecoveryGuard(locks, users, capability).requireRecoveryAccount());
        org.junit.jupiter.api.Assertions.assertEquals(409, error.getStatusCode().value());
    }

    private User user(String email, String role, boolean enabled, boolean persistedRecovery) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setAdminRecovery(persistedRecovery);
        return user;
    }
}
