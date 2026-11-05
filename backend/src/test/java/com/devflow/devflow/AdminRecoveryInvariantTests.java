package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.UserUpdateRequestDTO;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.RoleManagementService;
import com.devflow.devflow.service.UserService;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.security.CurrentUserAuthenticationService;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:devflow-recovery-invariant;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER",
    "devflow.bootstrap.admin.email=bootstrap-recovery@example.test"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminRecoveryInvariantTests {
    @Autowired private UserRepository users;
    @Autowired private RoleManagementService roles;
    @Autowired private UserService userService;
    @Autowired private EffectivePermissionService permissions;
    @Autowired private CurrentUserAuthenticationService currentAuthentication;

    @Test
    @Order(1)
    void lastRecoveryCapabilityCannotBeRemovedAndTransactionRollsBack() {
        User onlyAdmin = save("only", "ADMIN", true);
        try {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> roles.setRecoveryCapability(onlyAdmin.getId(), false, onlyAdmin.getEmail()));

            assertEquals(409, error.getStatusCode().value());
            assertTrue(users.findById(onlyAdmin.getId()).orElseThrow().hasAdminRecovery());
        } finally {
            users.delete(onlyAdmin);
        }
    }

    @Test
    @Order(2)
    void disablingTheLastRecoveryAccountRollsBackTheAccountChange() {
        User recovery = save("recovery", "ADMIN", true);
        User manager = save("manager", "ADMIN", false);
        try {
            UserUpdateRequestDTO update = new UserUpdateRequestDTO();
            update.setEnabled(false);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(recovery.getId(), update, manager.getEmail()));

            assertEquals(409, error.getStatusCode().value());
            User unchanged = users.findById(recovery.getId()).orElseThrow();
            assertTrue(unchanged.isEnabled());
            assertTrue(unchanged.hasAdminRecovery());
        } finally {
            users.deleteById(recovery.getId());
            users.deleteById(manager.getId());
        }
    }

    @Test
    @Order(5)
    void concurrentRecoveryRevocationsCannotRemoveTheLastAccount() throws Exception {
        User first = save("concurrent-a", "ADMIN", true);
        User second = save("concurrent-b", "ADMIN", true);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = executor.submit(() -> revokeAfter(start, second, first));
            Future<Boolean> b = executor.submit(() -> revokeAfter(start, first, second));
            start.countDown();
            boolean firstChanged = a.get();
            boolean secondChanged = b.get();
            long remaining = users.countByEnabledTrueAndAdminRecoveryTrue();
            assertTrue(remaining >= 1);
            assertTrue(firstChanged ^ secondChanged,
                "Exactly one competing revocation should succeed after the first actor loses recovery capability");
            boolean firstRecovery = users.findById(first.getId()).orElseThrow().hasAdminRecovery();
            boolean secondRecovery = users.findById(second.getId()).orElseThrow().hasAdminRecovery();
            assertFalse(!firstRecovery && !secondRecovery);
        }
    }

    @Test
    @Order(3)
    void configuredExistingAdminReceivesRecoveryCapabilityWithoutDatabaseFlagMutation() {
        User bootstrap = save("bootstrap-recovery", "ADMIN", false);
        bootstrap.setEmail("bootstrap-recovery@example.test");
        users.saveAndFlush(bootstrap);
        try {
            var effective = permissions.effective(bootstrap.getEmail());
            var authentication = currentAuthentication.authenticateEmail(bootstrap.getEmail());

            assertTrue(effective.recoveryCapable());
            assertTrue(effective.global().contains("ROLE_MANAGE"));
            assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> "RECOVERY_CAPABLE".equals(authority.getAuthority())));
            assertFalse(users.findById(bootstrap.getId()).orElseThrow().hasAdminRecovery(),
                "The runtime recovery designation must not rewrite the existing user row");
        } finally {
            users.deleteById(bootstrap.getId());
        }
    }

    @Test
    @Order(5)
    void configuredBootstrapAdminCannotBeDisabledAsTheLastRecoveryAccount() {
        User bootstrap = save("bootstrap-recovery", "ADMIN", false);
        bootstrap.setEmail("bootstrap-recovery@example.test");
        users.saveAndFlush(bootstrap);
        User ordinaryAdmin = save("ordinary-admin", "ADMIN", false);
        try {
            UserUpdateRequestDTO update = new UserUpdateRequestDTO();
            update.setEnabled(false);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(bootstrap.getId(), update, ordinaryAdmin.getEmail()));

            assertEquals(409, error.getStatusCode().value());
            assertTrue(users.findById(bootstrap.getId()).orElseThrow().isEnabled());
            assertFalse(users.findById(bootstrap.getId()).orElseThrow().hasAdminRecovery());
        } finally {
            users.deleteById(bootstrap.getId());
            users.deleteById(ordinaryAdmin.getId());
        }
    }

    private boolean revokeAfter(CountDownLatch start, User target, User actor) throws Exception {
        start.await();
        try {
            roles.setRecoveryCapability(target.getId(), false, actor.getEmail());
            return true;
        } catch (ResponseStatusException denied) {
            return false;
        }
    }

    private User save(String prefix, String role, boolean recovery) {
        User user = new User();
        user.setName(prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@recovery.test");
        user.setPassword("test-hash-not-used-for-authentication");
        user.setRole(role);
        user.setEnabled(true);
        user.setAdminRecovery(recovery);
        return users.saveAndFlush(user);
    }
}
