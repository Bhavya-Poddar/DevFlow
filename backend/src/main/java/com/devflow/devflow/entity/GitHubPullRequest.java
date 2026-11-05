package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "github_pull_request", uniqueConstraints = @UniqueConstraint(
    name = "uk_github_pull_request_repository_number",
    columnNames = { "repository_owner", "repository_name", "pull_request_number" }))
public class GitHubPullRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_github_pr_project"))
    private Project project;

    @Column(name = "repository_owner", nullable = false, length = 39)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false, length = 100)
    private String repositoryName;

    @Column(name = "pull_request_number", nullable = false)
    private Long pullRequestNumber;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "html_url", nullable = false, length = 2048)
    private String htmlUrl;

    @Column(length = 255)
    private String author;

    @Column(name = "head_branch", length = 255)
    private String headBranch;

    @Column(name = "base_branch", length = 255)
    private String baseBranch;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(nullable = false)
    private boolean merged;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "github_pull_request_issue",
        joinColumns = @JoinColumn(name = "pull_request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_github_pr_issue_pr")),
        inverseJoinColumns = @JoinColumn(name = "issue_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_github_pr_issue_issue")),
        uniqueConstraints = @UniqueConstraint(name = "uk_github_pr_issue", columnNames = { "pull_request_id", "issue_id" }))
    private Set<Issue> linkedIssues = new LinkedHashSet<>();

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public void setRepositoryOwner(String repositoryOwner) { this.repositoryOwner = repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public Long getPullRequestNumber() { return pullRequestNumber; }
    public void setPullRequestNumber(Long pullRequestNumber) { this.pullRequestNumber = pullRequestNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getHeadBranch() { return headBranch; }
    public void setHeadBranch(String headBranch) { this.headBranch = headBranch; }
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public boolean isMerged() { return merged; }
    public void setMerged(boolean merged) { this.merged = merged; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getMergedAt() { return mergedAt; }
    public void setMergedAt(LocalDateTime mergedAt) { this.mergedAt = mergedAt; }
    public Set<Issue> getLinkedIssues() { return linkedIssues; }
}
