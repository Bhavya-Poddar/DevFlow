package com.devflow.devflow.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.GitHubIssueActivity;

/** Safe, project-scoped activity item used by the team workspace. */
public record TeamActivityResponseDTO(
        String id,
        String sourceType,
        String action,
        String details,
        LocalDateTime occurredAt,
        Long actorId,
        String actorName,
        String actorAvatarUrl,
        Long taskId,
        String taskTitle,
        String htmlUrl,
        String repository) {

    public static TeamActivityResponseDTO fromAudit(AuditLog log) {
        var actor = log.getUser();
        var task = log.getIssue();
        return new TeamActivityResponseDTO("audit-" + log.getId(), "AUDIT", log.getAction(),
            log.getDetails(), log.getCreatedAt(), actor == null ? null : actor.getId(),
            actor == null ? null : actor.getName(), actor == null ? null : actor.getAvatarUrl(),
            task == null ? null : task.getId(), task == null ? null : task.getTitle(), null, null);
    }

    public static TeamActivityResponseDTO fromGitHub(GitHubIssueActivity activity) {
        var task = activity.getIssue();
        var delivery = activity.getDelivery();
        LocalDateTime occurredAt = activity.getEventAt() == null ? null
            : activity.getEventAt().atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return new TeamActivityResponseDTO("github-" + activity.getId(),
            "GITHUB_" + activity.getActivityType(), activity.getActivityType(), activity.getSummary(),
            occurredAt, null, activity.getAuthor(), null,
            task == null ? null : task.getId(), task == null ? null : task.getTitle(),
            activity.getHtmlUrl(), delivery.getRepositoryOwner() + "/" + delivery.getRepositoryName());
    }
}
