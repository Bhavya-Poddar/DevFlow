package com.devflow.devflow.dto;

import java.util.List;

public record DashboardSummaryDTO(
        long totalProjects,
        long totalIssues,
        long completedIssues,
        long activeIssues,
        long testingIssues,
        long totalSprints,
        long activeSprints,
        long teamMembers,
        List<StatusMetric> statusDistribution,
        List<ProjectProgress> projectProgress,
        List<TeamWorkload> teamWorkload) {

    public record StatusMetric(String status, long count) {}

    public record ProjectProgress(Long projectId, String projectName,
            long totalIssues, long completedIssues, int completionPercent) {}

    public record TeamWorkload(Long userId, String name, String role, long assignedIssues, String avatarUrl) {}
}
