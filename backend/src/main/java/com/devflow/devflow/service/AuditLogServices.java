package com.devflow.devflow.service;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.dto.AuditLogResponseDTO;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class AuditLogServices {
    @Autowired
    private AuditLogsRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditLog createLog(String action, String details, Long issueId, Long userId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());

        if (issueId != null) {
            log.setIssue(entityManager.getReference(Issue.class, issueId));
        }

        if (userId != null) {
            log.setUser(entityManager.getReference(User.class, userId));
        }

        return auditLogRepository.save(log);
    }

    public List<AuditLog> getLogsByIssue(Long issueId) {
        return auditLogRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDTO> searchLogs(Long actorId, String action, Long issueId,
            String outcome, LocalDateTime fromTime, LocalDateTime toTime, int page, int size) {
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
        String normalizedOutcome = outcome == null || outcome.isBlank()
                ? null : outcome.trim().toUpperCase(Locale.ROOT);
        if (normalizedOutcome != null && !List.of("SUCCESS", "FAILURE").contains(normalizedOutcome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome must be SUCCESS or FAILURE");
        }
        String normalizedAction = action == null || action.isBlank() ? null : action.trim();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return auditLogRepository.searchAuditLogs(actorId, normalizedAction, issueId,
                normalizedOutcome, fromTime, toTime, pageable).map(AuditLogResponseDTO::new);
    }

    public void detachIssueReference(Long issueId) {
        auditLogRepository.detachIssueReference(issueId);
    }

    public Long findActorId(String authenticatedEmail) {
        return userRepository.findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated account is no longer available"))
                .getId();
    }

    public void deleteLog(Long id) {
        deleteLog(id, null);
    }

    @Transactional
    public void deleteLog(Long id, Long actorId) {
        if (!auditLogRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found");
        }
        auditLogRepository.deleteById(id);
        if (actorId != null) {
            createLog("AUDIT_LOG_DELETED", "targetType=AUDIT_LOG; targetId=" + id + "; audit entry deleted", null, actorId);
        }
    }
}
