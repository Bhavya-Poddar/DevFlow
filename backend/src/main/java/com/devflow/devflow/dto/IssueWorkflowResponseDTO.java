package com.devflow.devflow.dto;

import java.time.LocalDateTime;

public class IssueWorkflowResponseDTO {
    private final Long id;
    private final String title;
    private final String description;
    private final String acceptanceCriteria;
    private final String priority;
    private final String status;
    private final Long projectId;
    private final String projectName;
    private final UserResponseDTO developer;
    private final UserResponseDTO tester;
    private final UserResponseDTO reportedBy;
    private final Long sprintId;
    private final String sprintName;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public IssueWorkflowResponseDTO(Long id, String title, String description, String acceptanceCriteria, String priority, String status,
            Long projectId, String projectName, UserResponseDTO developer, UserResponseDTO tester,
            UserResponseDTO reportedBy, Long sprintId, String sprintName,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.acceptanceCriteria = acceptanceCriteria;
        this.priority = priority;
        this.status = status;
        this.projectId = projectId;
        this.projectName = projectName;
        this.developer = developer;
        this.tester = tester;
        this.reportedBy = reportedBy;
        this.sprintId = sprintId;
        this.sprintName = sprintName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public Long getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public UserResponseDTO getDeveloper() { return developer; }
    public UserResponseDTO getTester() { return tester; }
    public UserResponseDTO getReportedBy() { return reportedBy; }
    public Long getSprintId() { return sprintId; }
    public String getSprintName() { return sprintName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
