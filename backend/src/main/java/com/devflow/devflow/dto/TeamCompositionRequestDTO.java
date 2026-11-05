package com.devflow.devflow.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Explicit responsibility selections used when provisioning a project's team. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamCompositionRequestDTO {
    private Long managerUserId;
    private List<Long> projectManagerIds = new ArrayList<>();
    private List<Long> teamLeadIds = new ArrayList<>();
    private List<Long> developerIds = new ArrayList<>();
    private List<Long> testerIds = new ArrayList<>();

    public Long getManagerUserId() { return managerUserId; }
    public void setManagerUserId(Long managerUserId) { this.managerUserId = managerUserId; }
    public List<Long> getProjectManagerIds() { return projectManagerIds; }
    public void setProjectManagerIds(List<Long> projectManagerIds) { this.projectManagerIds = projectManagerIds; }
    public List<Long> getTeamLeadIds() { return teamLeadIds; }
    public void setTeamLeadIds(List<Long> teamLeadIds) { this.teamLeadIds = teamLeadIds; }
    public List<Long> getDeveloperIds() { return developerIds; }
    public void setDeveloperIds(List<Long> developerIds) { this.developerIds = developerIds; }
    public List<Long> getTesterIds() { return testerIds; }
    public void setTesterIds(List<Long> testerIds) { this.testerIds = testerIds; }
}
