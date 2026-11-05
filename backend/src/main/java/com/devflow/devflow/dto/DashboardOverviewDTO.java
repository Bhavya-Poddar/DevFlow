package com.devflow.devflow.dto;

import java.util.List;

public record DashboardOverviewDTO(
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long overdueTasks,
        int completionRate,
        String overdueDefinition,
        List<DashboardSummaryDTO.StatusMetric> statusDistribution) {}
