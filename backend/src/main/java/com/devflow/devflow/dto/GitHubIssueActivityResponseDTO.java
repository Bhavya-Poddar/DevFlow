package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.GitHubIssueActivity;

public class GitHubIssueActivityResponseDTO {
    private final Long id;
    private final String type;
    private final String externalId;
    private final String summary;
    private final String htmlUrl;
    private final String author;
    private final LocalDateTime eventAt;
    private final String repositoryOwner;
    private final String repositoryName;

    public GitHubIssueActivityResponseDTO(GitHubIssueActivity activity) {
        id = activity.getId();
        type = activity.getActivityType();
        externalId = activity.getExternalId();
        summary = activity.getSummary();
        htmlUrl = activity.getHtmlUrl();
        author = activity.getAuthor();
        eventAt = activity.getEventAt();
        repositoryOwner = activity.getDelivery().getRepositoryOwner();
        repositoryName = activity.getDelivery().getRepositoryName();
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getExternalId() { return externalId; }
    public String getSummary() { return summary; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getAuthor() { return author; }
    public LocalDateTime getEventAt() { return eventAt; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
}
