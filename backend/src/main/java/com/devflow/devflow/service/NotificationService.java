package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.devflow.devflow.dto.NotificationResponseDTO;
import com.devflow.devflow.entity.Notification;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.NotificationRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    public Notification createNotification(User recipient, String type, String message) {
        if (recipient == null || recipient.getId() == null) {
            return null;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification savedNotification = notificationRepository.save(notification);
        try {
            messagingTemplate.convertAndSendToUser(
                recipient.getEmail(),
                "/queue/notifications",
                toResponse(savedNotification)
            );
        } catch (RuntimeException exception) {
            // Persisted notifications remain available through REST if live delivery fails.
            logger.warn("Could not send notification {} through WebSocket", savedNotification.getId(), exception);
        }
        return savedNotification;
    }

    public List<NotificationResponseDTO> getNotifications(String userEmail) {
        User user = getUser(userEmail);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
            .stream().map(this::toResponse).toList();
    }

    public List<NotificationResponseDTO> getUnreadNotifications(String userEmail) {
        User user = getUser(userEmail);
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId())
            .stream().map(this::toResponse).toList();
    }

    public NotificationResponseDTO markAsRead(Long notificationId, String userEmail) {
        User user = getUser(userEmail);
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    public void markAllAsRead(String userEmail) {
        User user = getUser(userEmail);
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private NotificationResponseDTO toResponse(Notification notification) {
        return new NotificationResponseDTO(
            notification.getId(),
            notification.getMessage(),
            notification.getType(),
            notification.isRead(),
            notification.getCreatedAt(),
            notification.getUser() == null ? null : notification.getUser().getAvatarUrl()
        );
    }
}
