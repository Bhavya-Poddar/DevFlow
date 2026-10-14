package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;
    @Column(columnDefinition = "TEXT")
    private String details;
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public AuditLog() {}

    public AuditLog(Long id, String action, String details, LocalDateTime createdAt, Issue issue, User user) {
        this.id = id;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
        this.issue = issue;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Issue getIssue() { return issue; }
    public void setIssue(Issue issue) { this.issue = issue; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
