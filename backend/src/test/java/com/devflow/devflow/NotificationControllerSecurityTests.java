package com.devflow.devflow;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.config.CorsConfig;
import com.devflow.devflow.controller.NotificationController;
import com.devflow.devflow.security.JwtAuthenticationFilter;
import com.devflow.devflow.security.CurrentUserAuthenticationService;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.security.SecurityConfig;
import com.devflow.devflow.service.NotificationService;

@WebMvcTest(
    controllers = NotificationController.class,
    properties = "devflow.jwt.secret=devflow-test-signing-key-12345678901234567890"
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CorsConfig.class})
class NotificationControllerSecurityTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private CurrentUserAuthenticationService currentUserAuthenticationService;

    @Test
    void notificationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isForbidden());
    }

    @Test
    void notificationRequestsUseTheJwtUserRatherThanAClientSuppliedUserId() throws Exception {
        String authorization = bearer("current@example.com", "VIEWER");

        mockMvc.perform(get("/api/notifications?userId=999").header("Authorization", authorization))
            .andExpect(status().isOk());
        verify(notificationService).getNotifications("current@example.com");

        mockMvc.perform(get("/api/notifications/unread?userId=999").header("Authorization", authorization))
            .andExpect(status().isOk());
        verify(notificationService).getUnreadNotifications("current@example.com");

        mockMvc.perform(put("/api/notifications/12/read?userId=999").header("Authorization", authorization))
            .andExpect(status().isOk());
        verify(notificationService).markAsRead(12L, "current@example.com");

        mockMvc.perform(put("/api/notifications/read-all?userId=999").header("Authorization", authorization))
            .andExpect(status().isNoContent());
        verify(notificationService).markAllAsRead("current@example.com");
    }

    private String bearer(String email, String role) {
        String token = jwtService.generateToken(email, role);
        org.mockito.Mockito.when(currentUserAuthenticationService.authenticateToken(token))
            .thenReturn(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                email, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        return "Bearer " + token;
    }
}
