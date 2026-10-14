package com.devflow.devflow.dto;

import java.util.List;

public class ProjectReportDTO {
    private final Long projectId;
    private final String projectName;
    private final String description;
    private final List<IssueReportRowDTO> issues;
    private final ProjectReportSummaryDTO summary;

    public ProjectReportDTO(Long projectId, String projectName, String description,
            List<IssueReportRowDTO> issues, ProjectReportSummaryDTO summary) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.description = description;
        this.issues = issues;
        this.summary = summary;
    }

    public Long getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public String getDescription() { return description; }
    public List<IssueReportRowDTO> getIssues() { return issues; }
    public ProjectReportSummaryDTO getSummary() { return summary; }
}
