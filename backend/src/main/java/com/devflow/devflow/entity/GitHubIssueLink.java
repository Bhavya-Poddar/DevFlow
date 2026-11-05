package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "github_issue_link", uniqueConstraints = {
    @UniqueConstraint(name = "uk_github_issue_link_task", columnNames = "issue_id"),
    @UniqueConstraint(name = "uk_github_issue_link_repo_number",
        columnNames = { "repository_owner", "repository_name", "github_issue_number" })
}, indexes = {
    @Index(name = "ix_github_issue_link_repository",
        columnList = "repository_owner, repository_name, state")
})
public class GitHubIssueLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_github_issue_link_task"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Issue issue;

    @Column(name = "repository_owner", nullable = false, length = 39)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false, length = 100)
    private String repositoryName;

    @Column(name = "github_issue_number")
    private Long githubIssueNumber;

    @Column(name = "html_url", length = 2048)
    private String htmlUrl;

    @Column(length = 512)
    private String title;

    /** CREATING, UNKNOWN, FAILED, or LINKED. */
    @Column(nullable = false, length = 16)
    private String state;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Issue getIssue() { return issue; }
    public void setIssue(Issue issue) { this.issue = issue; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public void setRepositoryOwner(String repositoryOwner) { this.repositoryOwner = repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public Long getGithubIssueNumber() { return githubIssueNumber; }
    public void setGithubIssueNumber(Long githubIssueNumber) { this.githubIssueNumber = githubIssueNumber; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
