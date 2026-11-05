package com.devflow.devflow;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthAuditTests {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditLogServices auditLogService;
    @InjectMocks private AuthService authService;

    @Test
    void successfulLoginAuditsActorWithoutRecordingTheIssuedToken() {
        User user = new User();
        user.setId(12L);
        user.setEmail("person@example.test");
        user.setPassword("stored-hash");
        user.setRole("DEVELOPER");
        when(userRepository.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("submitted-secret", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken(user.getEmail(), user.getRole())).thenReturn("sensitive-token");

        authService.loginUser(user.getEmail(), "submitted-secret");

        verify(auditLogService).createLog(eq("LOGIN_SUCCEEDED"),
                eq("outcome=SUCCESS; authentication succeeded"), eq(null), eq(12L));
    }

    @Test
    void failedLoginAuditDoesNotContainTheSubmittedEmailOrPassword() {
        when(userRepository.findByEmailIgnoreCase("private@example.test")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authService.loginUser("private@example.test", "submitted-password"));

        verify(auditLogService).createLog(eq("LOGIN_FAILED"),
                eq("outcome=FAILURE; login credentials rejected"), eq(null), eq(null));
    }

    @Test
    void wrongPasswordFailureIsAttributedToKnownAccountWithoutSensitiveDetails() {
        User user = new User();
        user.setId(19L);
        user.setEmail("known@example.test");
        user.setPassword("stored-hash");
        user.setEnabled(true);
        when(userRepository.findByEmailIgnoreCase("known@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-secret", "stored-hash")).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authService.loginUser("known@example.test", "wrong-secret"));

        verify(auditLogService).createLog(eq("LOGIN_FAILED"),
                eq("outcome=FAILURE; login credentials rejected"), eq(null), eq(19L));
    }
}
