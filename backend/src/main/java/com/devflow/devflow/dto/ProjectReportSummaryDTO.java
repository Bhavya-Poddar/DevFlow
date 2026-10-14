package com.devflow.devflow.dto;

public class ProjectReportSummaryDTO {
    private final long totalIssues;
    private final long completedIssues;
    private final long testingIssues;
    private final long passedTests;
    private final long failedTests;
    private final long unassignedIssues;

    public ProjectReportSummaryDTO(long totalIssues, long completedIssues, long testingIssues,
            long passedTests, long failedTests, long unassignedIssues) {
        this.totalIssues = totalIssues;
        this.completedIssues = completedIssues;
        this.testingIssues = testingIssues;
        this.passedTests = passedTests;
        this.failedTests = failedTests;
        this.unassignedIssues = unassignedIssues;
    }

    public long getTotalIssues() { return totalIssues; }
    public long getCompletedIssues() { return completedIssues; }
    public long getTestingIssues() { return testingIssues; }
    public long getPassedTests() { return passedTests; }
    public long getFailedTests() { return failedTests; }
    public long getUnassignedIssues() { return unassignedIssues; }
}
