package com.devflow.devflow.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.GitHubIssueActivity;

/** Safe unified view of persisted DevFlow audit entries and GitHub webhook activity. */
public class TaskActivityTimelineResponseDTO {
    private final String id;
    private final String sourceType;
    private final String action;
    private final String details;
    private final LocalDateTime occurredAt;
    private final String actorName;
    private final String actorAvatarUrl;
    private final String htmlUrl;
    private final String repository;

    private TaskActivityTimelineResponseDTO(String id, String sourceType, String action, String details,
            LocalDateTime occurredAt, String actorName, String actorAvatarUrl, String htmlUrl, String repository) {
        this.id = id;
        this.sourceType = sourceType;
        this.action = action;
        this.details = details;
        this.occurredAt = occurredAt;
        this.actorName = actorName;
        this.actorAvatarUrl = actorAvatarUrl;
        this.htmlUrl = htmlUrl;
        this.repository = repository;
    }

    public static TaskActivityTimelineResponseDTO fromAudit(AuditLog log) {
        return new TaskActivityTimelineResponseDTO("audit-" + log.getId(), "AUDIT", log.getAction(),
            log.getDetails(), log.getCreatedAt(), log.getUser() == null ? null : log.getUser().getName(),
            log.getUser() == null ? null : log.getUser().getAvatarUrl(), null, null);
    }

    public static TaskActivityTimelineResponseDTO fromGitHub(GitHubIssueActivity activity) {
        var delivery = activity.getDelivery();
        String repository = delivery.getRepositoryOwner() + "/" + delivery.getRepositoryName();
        // GitHub timestamps are parsed and stored as UTC LocalDateTime values. Audit
        // timestamps use the server's local time, so normalize GitHub values before
        // merging the two sources into one local-time timeline.
        LocalDateTime occurredAt = activity.getEventAt() == null ? null
            : activity.getEventAt().atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return new TaskActivityTimelineResponseDTO("github-" + activity.getId(),
            "GITHUB_" + activity.getActivityType(), activity.getActivityType(), activity.getSummary(),
            occurredAt, activity.getAuthor(), null, activity.getHtmlUrl(), repository);
    }

    public String getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getActorName() { return actorName; }
    public String getActorAvatarUrl() { return actorAvatarUrl; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getRepository() { return repository; }
}
