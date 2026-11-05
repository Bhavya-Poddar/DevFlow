package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.GitHubPullRequest;

public class GitHubPullRequestResponseDTO {
    private final Long id;
    private final Long number;
    private final String title;
    private final String htmlUrl;
    private final String author;
    private final String headBranch;
    private final String baseBranch;
    private final String state;
    private final boolean merged;
    private final String displayState;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime closedAt;
    private final LocalDateTime mergedAt;
    private final String repositoryOwner;
    private final String repositoryName;

    public GitHubPullRequestResponseDTO(GitHubPullRequest pullRequest) {
        id = pullRequest.getId();
        number = pullRequest.getPullRequestNumber();
        title = pullRequest.getTitle();
        htmlUrl = pullRequest.getHtmlUrl();
        author = pullRequest.getAuthor();
        headBranch = pullRequest.getHeadBranch();
        baseBranch = pullRequest.getBaseBranch();
        state = pullRequest.getState();
        merged = pullRequest.isMerged();
        displayState = merged ? "MERGED" : state.toUpperCase(java.util.Locale.ROOT);
        createdAt = pullRequest.getCreatedAt();
        updatedAt = pullRequest.getUpdatedAt();
        closedAt = pullRequest.getClosedAt();
        mergedAt = pullRequest.getMergedAt();
        repositoryOwner = pullRequest.getRepositoryOwner();
        repositoryName = pullRequest.getRepositoryName();
    }

    public Long getId() { return id; }
    public Long getNumber() { return number; }
    public String getTitle() { return title; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getAuthor() { return author; }
    public String getHeadBranch() { return headBranch; }
    public String getBaseBranch() { return baseBranch; }
    public String getState() { return state; }
    public boolean isMerged() { return merged; }
    public String getDisplayState() { return displayState; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public LocalDateTime getMergedAt() { return mergedAt; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
}
