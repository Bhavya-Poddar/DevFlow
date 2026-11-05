package com.devflow.devflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.config.CorsConfig;
import com.devflow.devflow.controller.NotificationController;
import com.devflow.devflow.security.JwtAuthenticationFilter;
import com.devflow.devflow.security.CurrentUserAuthenticationService;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.security.SecurityConfig;
import com.devflow.devflow.security.RequestPermissionAuthorizationManager;
import com.devflow.devflow.security.ProjectPrivacyInterceptor;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.RequestPermissionService;

@WebMvcTest(
    controllers = NotificationController.class,
    properties = "devflow.jwt.secret=devflow-test-signing-key-12345678901234567890"
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CorsConfig.class,
    RequestPermissionAuthorizationManager.class})
class NotificationControllerSecurityTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private CurrentUserAuthenticationService currentUserAuthenticationService;
    @MockitoBean private ProjectPrivacyInterceptor projectPrivacyInterceptor;
    @MockitoBean private RequestPermissionService requestPermissionService;

    @BeforeEach
    void allowUnrelatedNotificationRoutesThroughProjectPrivacyInterceptor() {
        when(projectPrivacyInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(requestPermissionService.isAllowed(any(), any())).thenReturn(true);
    }

    @Test
    void notificationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread/count")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/page"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeNotificationHistoryIsInvoked() throws Exception {
        String token = jwtService.generateToken("current@example.com", "VIEWER");
        when(currentUserAuthenticationService.authenticateToken(token)).thenReturn(null);

        mockMvc.perform(get("/api/notifications/page")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
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

    @Test
    void paginatedHistoryAndUnreadCountUseAuthenticatedPrincipal() throws Exception {
        String authorization = bearer("current@example.com", "VIEWER");
        when(notificationService.getUnreadCount("current@example.com")).thenReturn(3L);
        when(notificationService.getNotificationsPage("current@example.com", 2, 15, false, "issue"))
            .thenReturn(new PageImpl<>(java.util.List.of(
                new com.devflow.devflow.dto.NotificationResponseDTO(
                    7L, "Issue assigned", "ISSUE_ASSIGNED", false,
                    java.time.LocalDateTime.parse("2026-10-16T10:00:00"))),
                PageRequest.of(2, 15), 31));

        mockMvc.perform(get("/api/notifications/unread/count?userId=999")
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(3));
        verify(notificationService).getUnreadCount("current@example.com");

        mockMvc.perform(get("/api/notifications/page?page=2&size=15&read=false&search=issue&userId=999")
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(7))
            .andExpect(jsonPath("$.content[0].message").value("Issue assigned"))
            .andExpect(jsonPath("$.totalElements").value(31))
            .andExpect(jsonPath("$.size").value(15))
            .andExpect(jsonPath("$.number").value(2));
        verify(notificationService).getNotificationsPage("current@example.com", 2, 15, false, "issue");
    }

    private String bearer(String email, String role) {
        String token = jwtService.generateToken(email, role);
        org.mockito.Mockito.when(currentUserAuthenticationService.authenticateToken(token))
            .thenReturn(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                email, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        return "Bearer " + token;
    }
}
