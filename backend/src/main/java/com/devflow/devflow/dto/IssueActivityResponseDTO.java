package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.AuditLog;

/** Safe, read-only activity item without serializing JPA relationships. */
public class IssueActivityResponseDTO {
    private final Long id;
    private final Long issueId;
    private final String action;
    private final String details;
    private final LocalDateTime createdAt;
    private final Long actorId;
    private final String actorName;
    private final String actorAvatarUrl;

    public IssueActivityResponseDTO(AuditLog log) {
        this.id = log.getId();
        this.issueId = log.getIssue() == null ? null : log.getIssue().getId();
        this.action = log.getAction();
        this.details = log.getDetails();
        this.createdAt = log.getCreatedAt();
        this.actorId = log.getUser() == null ? null : log.getUser().getId();
        this.actorName = log.getUser() == null ? null : log.getUser().getName();
        this.actorAvatarUrl = log.getUser() == null ? null : log.getUser().getAvatarUrl();
    }

    public Long getId() { return id; }
    public Long getIssueId() { return issueId; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorAvatarUrl() { return actorAvatarUrl; }
}
