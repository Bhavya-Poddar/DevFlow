package com.devflow.devflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamCreateRequestDTO {
    private String name;
    private Long projectId;
    private TeamCompositionRequestDTO composition;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public TeamCompositionRequestDTO getComposition() { return composition; }
    public void setComposition(TeamCompositionRequestDTO composition) { this.composition = composition; }
}
