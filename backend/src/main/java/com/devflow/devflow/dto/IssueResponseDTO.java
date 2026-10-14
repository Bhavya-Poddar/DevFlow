package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.User;

/** Safe response for issue updates; relationship users are returned without credential fields. */
public class IssueResponseDTO {
    private final Long id;
    private final String title;
    private final String description;
    private final String acceptanceCriteria;
    private final String priority;
    private final String status;
    private final ProjectSummaryResponseDTO project;
    private final UserResponseDTO assignedUser;
    private final UserResponseDTO tester;
    private final UserResponseDTO reportedBy;
    private final SprintSummaryResponseDTO sprint;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public IssueResponseDTO(Issue issue) {
        this.id = issue.getId();
        this.title = issue.getTitle();
        this.description = issue.getDescription();
        this.acceptanceCriteria = issue.getAcceptanceCriteria();
        this.priority = issue.getPriority();
        this.status = issue.getStatus();
        this.project = issue.getProject() == null ? null : new ProjectSummaryResponseDTO(issue.getProject());
        this.assignedUser = toUserResponse(issue.getAssignedUser());
        this.tester = toUserResponse(issue.getTester());
        this.reportedBy = toUserResponse(issue.getReportedBy());
        this.sprint = issue.getSprint() == null ? null : new SprintSummaryResponseDTO(issue.getSprint());
        this.createdAt = issue.getCreatedAt();
        this.updatedAt = issue.getUpdatedAt();
    }

    private UserResponseDTO toUserResponse(User user) {
        return user == null ? null : new UserResponseDTO(user.getId(), user.getName(), user.getEmail(),
            user.getRole(), user.getAvatarUrl());
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public ProjectSummaryResponseDTO getProject() { return project; }
    public UserResponseDTO getAssignedUser() { return assignedUser; }
    public UserResponseDTO getTester() { return tester; }
    public UserResponseDTO getReportedBy() { return reportedBy; }
    public SprintSummaryResponseDTO getSprint() { return sprint; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
