package com.devflow.devflow.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Project creation input; the generated project ID is server-owned. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectCreateRequestDTO {
    private String name;
    private String description;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
