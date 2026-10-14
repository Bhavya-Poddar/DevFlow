package com.devflow.devflow.dto;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request body for the existing issue update endpoint. Setters track fields sent by the client. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueUpdateRequestDTO {
    private String title;
    private String description;
    private String acceptanceCriteria;
    private String priority;
    private String status;
    private IdReferenceRequestDTO project;
    private IdReferenceRequestDTO assignedUser;
    private IdReferenceRequestDTO tester;
    private IdReferenceRequestDTO sprint;
    private final Set<String> suppliedFields = new HashSet<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; suppliedFields.add("title"); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; suppliedFields.add("description"); }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; suppliedFields.add("acceptanceCriteria"); }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; suppliedFields.add("priority"); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; suppliedFields.add("status"); }
    public IdReferenceRequestDTO getProject() { return project; }
    public void setProject(IdReferenceRequestDTO project) { this.project = project; suppliedFields.add("project"); }
    public IdReferenceRequestDTO getAssignedUser() { return assignedUser; }
    public void setAssignedUser(IdReferenceRequestDTO assignedUser) { this.assignedUser = assignedUser; suppliedFields.add("assignedUser"); }
    public IdReferenceRequestDTO getTester() { return tester; }
    public void setTester(IdReferenceRequestDTO tester) { this.tester = tester; suppliedFields.add("tester"); }
    public IdReferenceRequestDTO getSprint() { return sprint; }
    public void setSprint(IdReferenceRequestDTO sprint) { this.sprint = sprint; suppliedFields.add("sprint"); }

    @JsonIgnore public boolean hasTitle() { return suppliedFields.contains("title"); }
    @JsonIgnore public boolean hasDescription() { return suppliedFields.contains("description"); }
    @JsonIgnore public boolean hasAcceptanceCriteria() { return suppliedFields.contains("acceptanceCriteria"); }
    @JsonIgnore public boolean hasPriority() { return suppliedFields.contains("priority"); }
    @JsonIgnore public boolean hasStatus() { return suppliedFields.contains("status"); }
    @JsonIgnore public boolean hasProject() { return suppliedFields.contains("project"); }
    @JsonIgnore public boolean hasAssignedUser() { return suppliedFields.contains("assignedUser"); }
    @JsonIgnore public boolean hasTester() { return suppliedFields.contains("tester"); }
    @JsonIgnore public boolean hasSprint() { return suppliedFields.contains("sprint"); }
}
