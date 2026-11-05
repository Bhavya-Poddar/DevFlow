package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.GitHubIssueLink;

public class GitHubIssueLinkResponseDTO {
    private final Long issueId;
    private final String repositoryOwner;
    private final String repositoryName;
    private final Long githubIssueNumber;
    private final String title;
    private final String htmlUrl;
    private final String state;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public GitHubIssueLinkResponseDTO(GitHubIssueLink link) {
        issueId = link.getIssue().getId();
        repositoryOwner = link.getRepositoryOwner();
        repositoryName = link.getRepositoryName();
        githubIssueNumber = link.getGithubIssueNumber();
        title = link.getTitle();
        htmlUrl = link.getHtmlUrl();
        state = link.getState();
        createdAt = link.getCreatedAt();
        updatedAt = link.getUpdatedAt();
    }

    public Long getIssueId() { return issueId; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
    public Long getGithubIssueNumber() { return githubIssueNumber; }
    public String getTitle() { return title; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
