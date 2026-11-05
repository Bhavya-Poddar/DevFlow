package com.devflow.devflow.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** New issue input using the established nested relationship ID format. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueCreateRequestDTO {
    private String title;
    private String description;
    private String acceptanceCriteria;
    private String priority;
    private String status;
    private LocalDate dueDate;
    private IdReferenceRequestDTO project;
    private IdReferenceRequestDTO assignedUser;
    private IdReferenceRequestDTO tester;
    private IdReferenceRequestDTO sprint;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public IdReferenceRequestDTO getProject() { return project; }
    public void setProject(IdReferenceRequestDTO project) { this.project = project; }
    public IdReferenceRequestDTO getAssignedUser() { return assignedUser; }
    public void setAssignedUser(IdReferenceRequestDTO assignedUser) { this.assignedUser = assignedUser; }
    public IdReferenceRequestDTO getTester() { return tester; }
    public void setTester(IdReferenceRequestDTO tester) { this.tester = tester; }
    public IdReferenceRequestDTO getSprint() { return sprint; }
    public void setSprint(IdReferenceRequestDTO sprint) { this.sprint = sprint; }
}
