package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.AuditLog;

/** Safe audit response that does not serialize JPA entities or user contact data. */
public record AuditLogResponseDTO(
        Long id,
        String action,
        String details,
        LocalDateTime createdAt,
        Long issueId,
        Long actorId,
        String actorName,
        String actorAvatarUrl,
        String outcome) {
    public AuditLogResponseDTO(AuditLog log) {
        this(log.getId(), log.getAction(), log.getDetails(), log.getCreatedAt(),
                log.getIssue() == null ? null : log.getIssue().getId(),
                log.getUser() == null ? null : log.getUser().getId(),
                log.getUser() == null ? null : log.getUser().getName(),
                log.getUser() == null ? null : log.getUser().getAvatarUrl(),
                "LOGIN_FAILED".equals(log.getAction()) ? "FAILURE" : "SUCCESS");
    }
}
