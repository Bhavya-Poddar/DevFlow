package com.devflow.devflow.dto;

import java.time.LocalDateTime;

public record PendingApprovalItemDTO(Long taskId, String taskName, Long projectId, String projectName,
        String developerName, String testerName, String stage, String testingResult,
        LocalDateTime testingCompletedAt, String teamLeadName, LocalDateTime teamLeadApprovedAt,
        LocalDateTime waitingSince, long waitingSeconds) { }
