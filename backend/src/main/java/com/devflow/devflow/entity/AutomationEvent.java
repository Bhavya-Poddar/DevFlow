package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Durable outbox row written in the same transaction as the task/testing operation. */
@Entity
@Table(name = "automation_event", uniqueConstraints =
    @UniqueConstraint(name = "uk_automation_event_source_key", columnNames = "source_key"))
public class AutomationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_key", nullable = false, length = 160)
    private String sourceKey;

    @Column(name = "trigger_event", nullable = false, length = 40)
    private String triggerEvent;

    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "resulting_status", length = 40)
    private String resultingStatus;

    @Column(nullable = false, length = 20)
    private String state = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_summary", length = 300)
    private String errorSummary;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (occurredAt == null) occurredAt = now;
        if (createdAt == null) createdAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getResultingStatus() { return resultingStatus; }
    public void setResultingStatus(String resultingStatus) { this.resultingStatus = resultingStatus; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
