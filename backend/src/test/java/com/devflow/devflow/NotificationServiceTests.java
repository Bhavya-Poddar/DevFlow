package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.NotificationResponseDTO;
import com.devflow.devflow.entity.Notification;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.NotificationRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.NotificationRecipientResolver;
import com.devflow.devflow.service.ProjectAccessService;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private NotificationRecipientResolver recipientResolver;
    @Mock private IssueRepository issueRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ProjectAccessService projectAccess;

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
    void targetPathIsPersistedAndReturnedForInternalNavigation() {
        User recipient = user(2L, "developer@example.com");
        Issue issue = new Issue();
        issue.setId(8L);
        when(issueRepository.findById(8L)).thenReturn(Optional.of(issue));
        when(projectAccess.canReadIssue(issue, recipient.getEmail())).thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(8L);
            return notification;
        });
        Notification saved = notificationService.createNotification(recipient, "ISSUE_ASSIGNED", "Assigned", "/issues?issueId=8");
        assertEquals("/issues?issueId=8", saved.getTargetPath());
        ArgumentCaptor<NotificationResponseDTO> response = ArgumentCaptor.forClass(NotificationResponseDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("developer@example.com"), eq("/queue/notifications"), response.capture());
        assertEquals("/issues?issueId=8", response.getValue().getTargetPath());
    }

    @Test
    void recipientFanoutDeduplicatesRelationshipsAndExcludesActorAndDisabledAccounts() {
        User actor = user(1L, "actor@example.com");
        User recipient = user(2L, "member@example.com");
        User disabled = user(3L, "disabled@example.com");
        Project project = new Project();
        project.setId(9L);
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));
        when(projectAccess.canReadProject(project, recipient.getEmail())).thenReturn(true);
        disabled.setEnabled(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(12L);
            return notification;
        });
        int created = notificationService.notifyUsers(List.of(recipient, recipient, actor, disabled), actor,
            "PROJECT_UPDATED", "Project changed", "/projects?projectId=9");
        assertEquals(1, created);
        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(eq("member@example.com"), eq("/queue/notifications"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("actor@example.com"), any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("disabled@example.com"), any(), any());
    }

    @Test
    void teamMembershipNoticeExcludesTheNewMemberFromExistingMemberBroadcast() {
        Team team = new Team();
        team.setId(9L);
        User actor = user(1L, "manager@example.com");
        User added = user(2L, "added@example.com");
        User existing = user(3L, "existing@example.com");
        when(teamRepository.findDetailedById(9L)).thenReturn(Optional.of(team));
        when(projectAccess.canReadTeam(team, existing.getEmail())).thenReturn(true);
        when(recipientResolver.teamParticipants(team)).thenReturn(List.of(actor, added, existing));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int count = notificationService.notifyTeamParticipantsExcept(team, actor, added.getId(), "TEAM_MEMBER_ADDED",
            "Added member joined", "/private-teams?teamId=9");

        assertEquals(1, count);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertEquals(existing, saved.getValue().getUser());
        assertEquals("Added member joined", saved.getValue().getMessage());
        verify(messagingTemplate).convertAndSendToUser(eq(existing.getEmail()), eq("/queue/notifications"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq(added.getEmail()), any(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq(actor.getEmail()), any(), any());
    }

    @Test
    void notificationTargetsUseExistingFrontendDetailRoutes() {
        assertEquals("/issues?issueId=8", com.devflow.devflow.service.NotificationTargetPaths.issue(8L));
        assertEquals("/projects?projectId=9", com.devflow.devflow.service.NotificationTargetPaths.project(9L));
        assertEquals("/private-teams?teamId=10", com.devflow.devflow.service.NotificationTargetPaths.team(10L));
    }

    @Test
    void rejectsExternalNotificationTargets() {
        assertThrows(IllegalArgumentException.class,
            () -> notificationService.createNotification(user(2L, "member@example.com"), "NOTICE", "Message", "//evil.test/path"));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void doesNotDeliverTaskNotificationAfterRecipientLosesProjectAccess() {
        User formerMember = user(6L, "former-member@example.com");
        Issue issue = new Issue();
        issue.setId(41L);
        when(issueRepository.findById(41L)).thenReturn(Optional.of(issue));
        when(projectAccess.canReadIssue(issue, formerMember.getEmail())).thenReturn(false);

        Notification result = notificationService.createNotification(formerMember, "ISSUE_STATUS_CHANGED",
            "Task status changed", "/issues?issueId=41");

        assertEquals(null, result);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq(formerMember.getEmail()), any(), any());
    }

    @Test
    void doesNotDeliverProjectNotificationWhenRecipientLacksProjectAccess() {
        User recipient = user(7L, "unassigned@example.com");
        Project project = new Project();
        project.setId(19L);
        when(projectRepository.findById(19L)).thenReturn(Optional.of(project));
        when(projectAccess.canReadProject(project, recipient.getEmail())).thenReturn(false);

        Notification result = notificationService.createNotification(recipient, "PROJECT_UPDATED",
            "Project changed", "/projects?projectId=19");

        assertEquals(null, result);
        verify(notificationRepository, never()).save(any(Notification.class));
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
    void unreadCountUsesDatabaseCountForAuthenticatedUser() {
        User currentUser = user(2L, "developer@example.com");
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.countByUserIdAndReadFalse(2L)).thenReturn(6L);

        assertEquals(6L, notificationService.getUnreadCount(currentUser.getEmail()));
        verify(notificationRepository).countByUserIdAndReadFalse(2L);
    }

    @Test
    void historyPageIsScopedAndSortedNewestThenIdDescending() {
        User currentUser = user(2L, "developer@example.com");
        Notification newest = new Notification();
        newest.setId(11L);
        newest.setUser(currentUser);
        newest.setCreatedAt(LocalDateTime.parse("2026-10-16T10:00:00"));
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.findHistoryPage(org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq("issue"),
                any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(newest)));

        var result = notificationService.getNotificationsPage(currentUser.getEmail(), 1, 10, false, " issue ");

        assertEquals(1, result.getNumberOfElements());
        assertEquals(11L, result.getContent().get(0).getId());
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
            ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(notificationRepository).findHistoryPage(org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq("issue"), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), pageable.getValue().getSort());
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
        ArgumentCaptor<Map> event = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq(currentUser.getEmail()), eq("/queue/notifications"), event.capture());
        assertEquals("READ", event.getValue().get("event"));
        assertEquals(4L, event.getValue().get("notificationId"));
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
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        notificationService.markAllAsRead(currentUser.getEmail());
        verify(notificationRepository).markAllUnreadRead(2L);
    }

    @Test
    void markingAllReadPublishesOnlyAUserScopedStateEventWhenUnreadRowsChanged() {
        User currentUser = user(2L, "developer@example.com");
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(notificationRepository.markAllUnreadRead(2L)).thenReturn(3);

        notificationService.markAllAsRead(currentUser.getEmail());

        ArgumentCaptor<Map> event = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq(currentUser.getEmail()), eq("/queue/notifications"), event.capture());
        assertEquals("READ_ALL", event.getValue().get("event"));
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}
