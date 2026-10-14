package com.devflow.devflow.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.devflow.devflow.entity.AuditLog;

public interface AuditLogsRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    /** Keep audit entries when an issue is removed, while releasing its foreign-key reference. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditLog log set log.issue = null where log.issue.id = :issueId")
    int detachIssueReference(@Param("issueId") Long issueId);
}
