package com.devflow.devflow.dto;

import java.time.LocalDateTime;

public class IssueReportRowDTO {
    private final Long id;
    private final String title;
    private final String priority;
    private final String status;
    private final UserResponseDTO developer;
    private final UserResponseDTO tester;
    private final LocalDateTime reportedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String testStatus;
    private final String testResult;
    private final LocalDateTime testStartedAt;
    private final LocalDateTime testCompletedAt;
    private final String testNotes;

    public IssueReportRowDTO(Long id, String title, String priority, String status,
            UserResponseDTO developer, UserResponseDTO tester, LocalDateTime reportedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt, String testStatus, String testResult,
            LocalDateTime testStartedAt, LocalDateTime testCompletedAt, String testNotes) {
        this.id = id;
        this.title = title;
        this.priority = priority;
        this.status = status;
        this.developer = developer;
        this.tester = tester;
        this.reportedAt = reportedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.testStatus = testStatus;
        this.testResult = testResult;
        this.testStartedAt = testStartedAt;
        this.testCompletedAt = testCompletedAt;
        this.testNotes = testNotes;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public UserResponseDTO getDeveloper() { return developer; }
    public UserResponseDTO getTester() { return tester; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getTestStatus() { return testStatus; }
    public String getTestResult() { return testResult; }
    public LocalDateTime getTestStartedAt() { return testStartedAt; }
    public LocalDateTime getTestCompletedAt() { return testCompletedAt; }
    public String getTestNotes() { return testNotes; }
}
