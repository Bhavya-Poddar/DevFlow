package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Project creation input; the generated project ID is server-owned. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectCreateRequestDTO {
    private String name;
    private String description;
    private String status;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long teamId;
    private TeamCompositionRequestDTO teamComposition;
    private String teamName;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public TeamCompositionRequestDTO getTeamComposition() { return teamComposition; }
    public void setTeamComposition(TeamCompositionRequestDTO teamComposition) { this.teamComposition = teamComposition; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
}
