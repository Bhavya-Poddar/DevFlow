package com.devflow.devflow.dto;

import java.time.LocalDate;

import com.devflow.devflow.entity.Project;

public class ProjectSummaryResponseDTO {
    private final Long id;
    private final String name;
    private final String description;
    private final String status;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public ProjectSummaryResponseDTO(Project project) {
        this.id = project.getId();
        this.name = project.getName();
        this.description = project.getDescription();
        this.status = project.getStatus();
        this.startDate = project.getStartDate();
        this.endDate = project.getEndDate();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
