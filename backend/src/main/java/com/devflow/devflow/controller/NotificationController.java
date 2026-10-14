package com.devflow.devflow.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.NotificationResponseDTO;
import com.devflow.devflow.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getNotifications(authentication.getName()));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDTO>> getUnreadNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(authentication.getName()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDTO> markAsRead(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAsRead(id, authentication.getName()));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
