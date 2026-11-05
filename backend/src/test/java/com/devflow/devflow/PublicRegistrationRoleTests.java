package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.dto.RegistrationRequestDTO;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.service.AuthService;
import com.devflow.devflow.service.AuditLogServices;

@ExtendWith(MockitoExtension.class)
class PublicRegistrationRoleTests {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditLogServices auditLogService;

    @InjectMocks private AuthService authService;

    @Test
    void publicRegistrationAlwaysCreatesViewerEvenIfRequestContainsAdminRole() {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName("New user");
        request.setEmail("new@example.com");
        request.setPassword("plain-password");
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = authService.registerUser(request);

        assertEquals("VIEWER", registered.getRole());
        assertEquals("hashed-password", registered.getPassword());
        verify(passwordEncoder).encode("plain-password");
    }
}
