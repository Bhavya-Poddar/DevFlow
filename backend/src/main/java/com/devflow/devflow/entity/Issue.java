package com.devflow.devflow.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Issue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String description;
    @Column(columnDefinition = "TEXT")
    private String acceptanceCriteria;
    private String priority;
    private String status;

    @Column(name = "workflow_cycle", nullable = false)
    private int workflowCycle = 1;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne @JoinColumn(name = "tester_id")
    private User tester;

    @ManyToOne @JoinColumn(name = "reported_by_id")
    private User reportedBy;

    @ManyToOne @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void setUpdateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public Issue() {}

    public Issue(Long id, String title, String description, String priority, String status, Project project, User assignedUser, Sprint sprint) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.project = project;
        this.assignedUser = assignedUser;
        this.sprint = sprint;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public int getWorkflowCycle() { return workflowCycle; }
    public void setWorkflowCycle(int workflowCycle) { this.workflowCycle = workflowCycle; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public User getAssignedUser() { return assignedUser; }
    public void setAssignedUser(User assignedUser) { this.assignedUser = assignedUser; }
    public User getTester() { return tester; }
    public void setTester(User tester) { this.tester = tester; }
    public User getReportedBy() { return reportedBy; }
    public void setReportedBy(User reportedBy) { this.reportedBy = reportedBy; }
    public Sprint getSprint() { return sprint; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
