package com.devflow.devflow.dto;

import java.time.LocalDateTime;

public class TestExecutionResponseDTO {
    private final Long id;
    private final Long issueId;
    private final CollaboratorResponseDTO tester;
    private final String status;
    private final String result;
    private final String testScenario;
    private final String expectedResult;
    private final String actualResult;
    private final String notes;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final LocalDateTime createdAt;

    public TestExecutionResponseDTO(Long id, Long issueId, CollaboratorResponseDTO tester, String status,
            String result, String testScenario, String expectedResult, String actualResult, String notes, LocalDateTime startedAt, LocalDateTime completedAt,
            LocalDateTime createdAt) {
        this.id = id;
        this.issueId = issueId;
        this.tester = tester;
        this.status = status;
        this.result = result;
        this.testScenario = testScenario;
        this.expectedResult = expectedResult;
        this.actualResult = actualResult;
        this.notes = notes;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getIssueId() { return issueId; }
    public CollaboratorResponseDTO getTester() { return tester; }
    public String getStatus() { return status; }
    public String getResult() { return result; }
    public String getTestScenario() { return testScenario; }
    public String getExpectedResult() { return expectedResult; }
    public String getActualResult() { return actualResult; }
    public String getNotes() { return notes; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
