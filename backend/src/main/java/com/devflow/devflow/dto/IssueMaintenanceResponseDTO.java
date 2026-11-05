package com.devflow.devflow.dto;
import java.time.LocalDateTime;
import java.util.List;
public record IssueMaintenanceResponseDTO(Long issueId, String status, LocalDateTime startedAt,
        LocalDateTime expiresAt, LocalDateTime completedAt, List<FollowUp> followUps) {
    public record FollowUp(Long issueId, String title, String reason, CollaboratorResponseDTO reporter,
            CollaboratorResponseDTO responsibleDeveloper, LocalDateTime reportedAt) {}
}
