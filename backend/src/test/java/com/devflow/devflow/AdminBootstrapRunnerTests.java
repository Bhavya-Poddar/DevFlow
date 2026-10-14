package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.devflow.devflow.config.AdminBootstrapRunner;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTests {

    @Mock private UserRepository userRepository;

    @Test
    void createsBcryptHashedAdminWhenConfiguredUserIsMissing() throws Exception {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
            userRepository, passwordEncoder, "admin@example.com", "bootstrap-password", "Bootstrap Admin");

        runner.run();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        User admin = savedUser.getValue();
        assertEquals("Bootstrap Admin", admin.getName());
        assertEquals("admin@example.com", admin.getEmail());
        assertEquals("ADMIN", admin.getRole());
        assertFalse("bootstrap-password".equals(admin.getPassword()));
        assertTrue(passwordEncoder.matches("bootstrap-password", admin.getPassword()));
    }

    @Test
    void existingAdminIsNotChangedOrDuplicated() throws Exception {
        User existingAdmin = new User(5L, "Existing", "admin@example.com", "stored-hash", "VIEWER");
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(existingAdmin));
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
            userRepository, new BCryptPasswordEncoder(), "admin@example.com", "new-password", "New Name");

        runner.run();

        assertEquals("stored-hash", existingAdmin.getPassword());
        assertEquals("VIEWER", existingAdmin.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void missingConfigurationSkipsBootstrapWithoutRepositoryAccess() throws Exception {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
            userRepository, new BCryptPasswordEncoder(), "", "", "");

        runner.run();

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void partialConfigurationAlsoSkipsBootstrap() throws Exception {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
            userRepository, new BCryptPasswordEncoder(), "admin@example.com", "", "Bootstrap Admin");

        runner.run();

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any(User.class));
    }
}
