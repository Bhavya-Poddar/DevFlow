package com.devflow.devflow.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.NotificationResponseDTO;
import com.devflow.devflow.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Authenticated user's notification history and read state.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    @Autowired private NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List notifications", description = "Returns the authenticated user's notifications.")
    public ResponseEntity<List<NotificationResponseDTO>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getNotifications(authentication.getName()));
    }

    @GetMapping("/unread")
    @Operation(summary = "List unread notifications", description = "Returns unread notifications belonging to the authenticated user.")
    public ResponseEntity<List<NotificationResponseDTO>> getUnreadNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(authentication.getName()));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "Get unread notification count", description = "Returns a count of unread notifications for the authenticated user.")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getUnreadCount(authentication.getName()));
    }

    @GetMapping("/page")
    @Operation(summary = "Get a notification history page", description = "Returns newest-first paginated history with optional read-state and search filters.")
    public ResponseEntity<Page<NotificationResponseDTO>> getNotificationPage(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(notificationService.getNotificationsPage(
            authentication.getName(), page, size, read, search));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read", description = "Marks one of the authenticated user's notifications as read.")
    public ResponseEntity<NotificationResponseDTO> markAsRead(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAsRead(id, authentication.getName()));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks all notifications belonging to the authenticated user as read.")
    @ApiResponse(responseCode = "204", description = "All matching notifications were marked read.")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
