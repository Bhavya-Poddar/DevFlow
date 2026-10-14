package com.devflow.devflow.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Sprint creation input; the generated sprint ID is server-owned. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SprintCreateRequestDTO {
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private IdReferenceRequestDTO project;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public IdReferenceRequestDTO getProject() { return project; }
    public void setProject(IdReferenceRequestDTO project) { this.project = project; }
}
