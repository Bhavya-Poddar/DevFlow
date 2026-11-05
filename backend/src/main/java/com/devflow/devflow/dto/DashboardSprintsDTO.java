package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardSprintsDTO(List<ActiveSprint> activeSprints) {
    public record ActiveSprint(Long sprintId, String sprintName, String status, Long projectId,
            String projectName, LocalDate startDate, LocalDate endDate, long totalTasks,
            long completedTasks, int completionRate) {}
}
