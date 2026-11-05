package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.dto.NotificationResponseDTO;
import com.devflow.devflow.entity.Notification;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.NotificationRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private NotificationRecipientResolver recipientResolver;
    @Autowired private IssueRepository issueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private ProjectAccessService projectAccess;

    public Notification createNotification(User recipient, String type, String message) {
        return createNotification(recipient, type, message, null);
    }

    public Notification createNotification(User recipient, String type, String message, String targetPath) {
        if (recipient == null || recipient.getId() == null || !recipient.isEnabled()) {
            return null;
        }
        Target target = targetFromPath(targetPath);
        if (target != null) {
            if (projectAccess == null) return null;
            boolean canRead = switch (target.type()) {
                case "issue" -> issueRepository.findById(target.id())
                    .map(issue -> projectAccess.canReadIssue(issue, recipient.getEmail())).orElse(false);
                case "project" -> projectRepository.findById(target.id())
                    .map(project -> projectAccess.canReadProject(project, recipient.getEmail())).orElse(false);
                case "team" -> teamRepository.findDetailedById(target.id())
                    .map(team -> projectAccess.canReadTeam(team, recipient.getEmail())).orElse(false);
                default -> true;
            };
            if (!canRead) return null;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setTargetPath(safeTargetPath(targetPath));
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification savedNotification = notificationRepository.save(notification);
        Runnable publish = () -> publishRealtime(savedNotification, recipient);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
        return savedNotification;
    }

    private Target targetFromPath(String targetPath) {
        if (targetPath == null) return null;
        String path = targetPath.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "^/(issues\\?issueId=|projects\\?projectId=|private-teams\\?teamId=)([1-9][0-9]*)$")
            .matcher(path);
        if (!matcher.matches()) return null;
        try {
            String type = matcher.group(1).startsWith("issues") ? "issue"
                : matcher.group(1).startsWith("projects") ? "project" : "team";
            return new Target(type, Long.valueOf(matcher.group(2)));
        }
        catch (NumberFormatException invalidId) { return null; }
    }

    private record Target(String type, Long id) { }

    public int notifyProjectParticipants(com.devflow.devflow.entity.Project project, User actor,
            String type, String message, String targetPath) {
        return notifyUsers(recipientResolver.projectParticipants(project), actor, type, message, targetPath);
    }

    public int notifyTeamParticipants(com.devflow.devflow.entity.Team team, User actor,
            String type, String message, String targetPath) {
        return notifyUsers(recipientResolver.teamParticipants(team), actor, type, message, targetPath);
    }

    public int notifyTeamParticipantsExcept(com.devflow.devflow.entity.Team team, User actor, Long excludedUserId,
            String type, String message, String targetPath) {
        Collection<User> recipients = recipientResolver.teamParticipants(team).stream()
            .filter(user -> !java.util.Objects.equals(user.getId(), excludedUserId)).toList();
        return notifyUsers(recipients, actor, type, message, targetPath);
    }

    public int notifyIssueParticipants(com.devflow.devflow.entity.Issue issue, User actor,
            String type, String message, String targetPath) {
        return notifyUsers(recipientResolver.issueParticipants(issue), actor, type, message, targetPath);
    }

    public int notifyUsers(Collection<User> recipients, User actor, String type, String message, String targetPath) {
        if (recipients == null) return 0;
        Long actorId = actor == null ? null : actor.getId();
        Map<Long, User> unique = new LinkedHashMap<>();
        for (User recipient : recipients) {
            if (recipient != null && recipient.getId() != null && recipient.isEnabled()
                    && !recipient.getId().equals(actorId)) unique.putIfAbsent(recipient.getId(), recipient);
        }
        unique.values().forEach(recipient -> createNotification(recipient, type, message, targetPath));
        return unique.size();
    }

    private String safeTargetPath(String targetPath) {
        if (targetPath == null || targetPath.isBlank()) return null;
        String path = targetPath.trim();
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")
                || path.contains("\r") || path.contains("\n") || path.length() > 512) {
            throw new IllegalArgumentException("Notification target must be a same-origin application path");
        }
        return path;
    }

    private void publishRealtime(Notification notification, User recipient) {
        try {
            messagingTemplate.convertAndSendToUser(
                recipient.getEmail(), "/queue/notifications", toResponse(notification));
        } catch (RuntimeException exception) {
            // Persisted notifications remain available through REST if live delivery fails.
            logger.warn("Could not send notification {} through WebSocket", notification.getId(), exception);
        }
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

    public long getUnreadCount(String userEmail) {
        User user = getUser(userEmail);
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    public Page<NotificationResponseDTO> getNotificationsPage(String userEmail, int page, int size,
            Boolean readState, String search) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
        }
        User user = getUser(userEmail);
        String cleanedSearch = search == null || search.isBlank() ? null : search.trim();
        Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return notificationRepository.findHistoryPage(user.getId(), readState, cleanedSearch, pageable)
            .map(this::toResponse);
    }

    @Transactional
    public NotificationResponseDTO markAsRead(Long notificationId, String userEmail) {
        User user = getUser(userEmail);
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        boolean wasUnread = !notification.isRead();
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        if (wasUnread) publishReadState(user, "READ", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void markAllAsRead(String userEmail) {
        User user = getUser(userEmail);
        if (notificationRepository.markAllUnreadRead(user.getId()) > 0) {
            publishReadState(user, "READ_ALL", null);
        }
    }

    private void publishReadState(User user, String event, Long notificationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        if (notificationId != null) payload.put("notificationId", notificationId);
        Runnable publish = () -> {
            try {
                messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications", payload);
            } catch (RuntimeException exception) {
                logger.warn("Could not publish notification read-state event for user {}", user.getId(), exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
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
            notification.getUser() == null ? null : notification.getUser().getAvatarUrl(),
            notification.getTargetPath()
        );
    }
}
