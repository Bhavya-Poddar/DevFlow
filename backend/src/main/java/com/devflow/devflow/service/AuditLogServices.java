package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuditLogsRepository;

@Service
public class AuditLogServices {
    @Autowired
    private AuditLogsRepository auditLogRepository;

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

    public void detachIssueReference(Long issueId) {
        auditLogRepository.detachIssueReference(issueId);
    }

    public void deleteLog(Long id) {
        if (!auditLogRepository.existsById(id)) throw new RuntimeException("Audit log not found");
        auditLogRepository.deleteById(id);
    }
}
