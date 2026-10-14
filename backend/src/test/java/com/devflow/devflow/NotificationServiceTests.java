package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.NotificationResponseDTO;
import com.devflow.devflow.entity.Notification;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.NotificationRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private NotificationService notificationService;

    @Test
    void createsPersistsAndSendsNotificationToItsUser() {
        User recipient = user(2L, "developer@example.com");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(7L);
            return notification;
        });

        Notification saved = notificationService.createNotification(recipient, "ISSUE_ASSIGNED", "Assigned to you");

        assertEquals(7L, saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertFalse(saved.isRead());
        assertEquals(recipient, saved.getUser());
        verify(messagingTemplate).convertAndSendToUser(
            org.mockito.ArgumentMatchers.eq("developer@example.com"),
            org.mockito.ArgumentMatchers.eq("/queue/notifications"),
            any(NotificationResponseDTO.class));
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersNotifications() {
        User currentUser = user(2L, "developer@example.com");
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        assertEquals(List.of(), notificationService.getNotifications(currentUser.getEmail()));
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(2L);
    }

    @Test
    void marksOwnNotificationRead() {
        User currentUser = user(2L, "developer@example.com");
        Notification notification = new Notification();
        notification.setId(4L);
        notification.setUser(currentUser);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.findByIdAndUserId(4L, 2L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponseDTO result = notificationService.markAsRead(4L, currentUser.getEmail());

        assertEquals(4L, result.getId());
        assertEquals(true, result.isRead());
    }

    @Test
    void cannotMarkAnotherUsersNotificationRead() {
        User currentUser = user(2L, "developer@example.com");
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.findByIdAndUserId(4L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
            () -> notificationService.markAsRead(4L, currentUser.getEmail()));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void marksOnlyCurrentUsersUnreadNotificationsRead() {
        User currentUser = user(2L, "developer@example.com");
        Notification notification = new Notification();
        notification.setRead(false);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(2L))
            .thenReturn(List.of(notification));

        notificationService.markAllAsRead(currentUser.getEmail());

        assertEquals(true, notification.isRead());
        verify(notificationRepository).saveAll(List.of(notification));
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}
