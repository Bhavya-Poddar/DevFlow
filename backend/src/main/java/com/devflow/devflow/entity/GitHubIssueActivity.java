package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "github_issue_activity", uniqueConstraints = @UniqueConstraint(
    name = "uk_github_activity_delivery_object_issue",
    columnNames = { "delivery_id", "external_id", "issue_id" }))
public class GitHubIssueActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_github_activity_issue"))
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_github_activity_delivery"))
    private GitHubWebhookDelivery delivery;

    @Column(name = "activity_type", nullable = false, length = 24)
    private String activityType;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "html_url", nullable = false, length = 2048)
    private String htmlUrl;

    @Column(length = 255)
    private String author;

    @Column(name = "event_at")
    private LocalDateTime eventAt;

    public Long getId() { return id; }
    public Issue getIssue() { return issue; }
    public void setIssue(Issue issue) { this.issue = issue; }
    public GitHubWebhookDelivery getDelivery() { return delivery; }
    public void setDelivery(GitHubWebhookDelivery delivery) { this.delivery = delivery; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public LocalDateTime getEventAt() { return eventAt; }
    public void setEventAt(LocalDateTime eventAt) { this.eventAt = eventAt; }
}
