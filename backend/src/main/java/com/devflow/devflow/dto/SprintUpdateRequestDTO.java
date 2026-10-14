package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Partial update input. Omitted fields keep their existing values. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SprintUpdateRequestDTO {
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private IdReferenceRequestDTO project;
    private final Set<String> suppliedFields = new HashSet<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; suppliedFields.add("name"); }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; suppliedFields.add("goal"); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; suppliedFields.add("startDate"); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; suppliedFields.add("endDate"); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; suppliedFields.add("status"); }
    public IdReferenceRequestDTO getProject() { return project; }
    public void setProject(IdReferenceRequestDTO project) { this.project = project; suppliedFields.add("project"); }

    @JsonIgnore public boolean hasName() { return suppliedFields.contains("name"); }
    @JsonIgnore public boolean hasGoal() { return suppliedFields.contains("goal"); }
    @JsonIgnore public boolean hasStartDate() { return suppliedFields.contains("startDate"); }
    @JsonIgnore public boolean hasEndDate() { return suppliedFields.contains("endDate"); }
    @JsonIgnore public boolean hasStatus() { return suppliedFields.contains("status"); }
    @JsonIgnore public boolean hasProject() { return suppliedFields.contains("project"); }
}
