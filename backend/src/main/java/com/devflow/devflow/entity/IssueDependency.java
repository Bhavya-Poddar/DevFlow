package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A directed issue relationship: blockedIssue is blocked by blockingIssue. */
@Entity
@Table(name = "issue_dependency",
    uniqueConstraints = @UniqueConstraint(name = "uk_issue_dependency_pair",
        columnNames = { "blocked_issue_id", "blocking_issue_id" }),
    indexes = @Index(name = "ix_issue_dependency_blocker", columnList = "blocking_issue_id"))
public class IssueDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_issue_id", nullable = false)
    private Issue blockedIssue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocking_issue_id", nullable = false)
    private Issue blockingIssue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Issue getBlockedIssue() { return blockedIssue; }
    public void setBlockedIssue(Issue blockedIssue) { this.blockedIssue = blockedIssue; }
    public Issue getBlockingIssue() { return blockingIssue; }
    public void setBlockingIssue(Issue blockingIssue) { this.blockingIssue = blockingIssue; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
