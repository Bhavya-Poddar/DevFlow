package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.devflow.devflow.entity.Notification;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.NotificationRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.NotificationService;

@SpringBootTest
class NotificationPaginationIntegrationTests {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    private final List<User> users = new ArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private User firstUser;
    private User secondUser;

    @BeforeEach
    void createUsers() {
        String run = UUID.randomUUID().toString();
        firstUser = saveUser("first-" + run + "@example.test");
        secondUser = saveUser("second-" + run + "@example.test");
    }

    @AfterEach
    void removeTestRecords() {
        notificationRepository.deleteAll(notifications);
        userRepository.deleteAll(users);
    }

    @Test
    void historyIsUserScopedPagedAndStableForEqualTimestamps() {
        LocalDateTime sameTime = LocalDateTime.parse("2026-10-15T12:00:00");
        Notification first = saveNotification(firstUser, "Issue assigned", false, sameTime);
        Notification second = saveNotification(firstUser, "Issue updated", false, sameTime);
        saveNotification(firstUser, "Old read item", true, sameTime.minusDays(1));
        saveNotification(secondUser, "Other user's issue", false, sameTime.plusDays(1));

        var newestPage = notificationService.getNotificationsPage(firstUser.getEmail(), 0, 1, null, null);
        var nextPage = notificationService.getNotificationsPage(firstUser.getEmail(), 1, 1, null, null);

        assertEquals(3, newestPage.getTotalElements());
        assertEquals(1, newestPage.getContent().size());
        assertEquals(second.getId(), newestPage.getContent().get(0).getId());
        assertEquals(first.getId(), nextPage.getContent().get(0).getId());
        assertEquals(2, notificationService.getUnreadCount(firstUser.getEmail()));
        assertEquals(2, notificationService.getNotificationsPage(firstUser.getEmail(), 0, 10, false, "issue")
            .getTotalElements());
    }

    private User saveUser(String email) {
        User user = new User();
        user.setName("Notification test user");
        user.setEmail(email);
        user.setPassword("not-a-real-password");
        user.setRole("VIEWER");
        users.add(userRepository.saveAndFlush(user));
        return users.get(users.size() - 1);
    }

    private Notification saveNotification(User user, String message, boolean read, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setMessage(message);
        notification.setType("TEST");
        notification.setRead(read);
        notification.setCreatedAt(createdAt);
        notifications.add(notificationRepository.saveAndFlush(notification));
        return notifications.get(notifications.size() - 1);
    }
}
