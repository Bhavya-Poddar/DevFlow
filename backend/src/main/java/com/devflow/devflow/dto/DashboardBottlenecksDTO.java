package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardBottlenecksDTO(List<BlockedTask> blockedTasks, List<OverdueTask> overdueTasks,
        String overdueDefinition) {
    public record BlockedTask(Long taskId, String title, String status, Long projectId, String projectName,
            Long blockingTaskId, String blockingTaskTitle, String blockingTaskStatus, String assigneeName) {}

    public record OverdueTask(Long taskId, String title, String status, Long projectId, String projectName,
            Long sprintId, String sprintName, LocalDate sprintEndDate, String assigneeName) {}
}
