package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Column;

@Entity
public class TestExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne @JoinColumn(name = "issue_id")
    private Issue issue;
    @ManyToOne @JoinColumn(name = "tester_id")
    private User tester;
    private String status;
    private String result;

    @Column(name = "workflow_cycle", nullable = false)
    private int workflowCycle = 1;
    @Column(columnDefinition = "TEXT")
    private String testScenario;
    @Column(columnDefinition = "TEXT")
    private String expectedResult;
    @Column(columnDefinition = "TEXT")
    private String actualResult;
    @Column(columnDefinition = "TEXT")
    private String notes;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    @PrePersist
    public void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Issue getIssue() { return issue; }
    public void setIssue(Issue issue) { this.issue = issue; }
    public User getTester() { return tester; }
    public void setTester(User tester) { this.tester = tester; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public int getWorkflowCycle() { return workflowCycle; }
    public void setWorkflowCycle(int workflowCycle) { this.workflowCycle = workflowCycle; }
    public String getTestScenario() { return testScenario; }
    public void setTestScenario(String testScenario) { this.testScenario = testScenario; }
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
