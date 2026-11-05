package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.List;

/** Current, database-backed delivery risks for the selected dashboard scope. */
public record DashboardWorkloadRiskDTO(
        LocalDate asOf,
        LocalDate approachingDeadlineThrough,
        List<RiskTask> overdueTasks,
        List<RiskTask> approachingDeadlineTasks,
        List<AssigneeLoad> unresolvedByAssignee,
        List<SprintRisk> sprintRisks,
        String definitions) {

    public record RiskTask(Long taskId, String title, String status, LocalDate dueDate,
            Long projectId, String projectName, Long sprintId, String sprintName, String assigneeName) {}

    public record AssigneeLoad(Long userId, String name, String avatarUrl, long unresolvedTasks) {}

    public record SprintRisk(Long sprintId, String sprintName, String status, Long projectId,
            String projectName, LocalDate endDate, long totalTasks, long completedTasks,
            long unresolvedTasks, long overdueTasks, long approachingDeadlineTasks,
            int completionRate, String riskLevel) {}
}
