package com.devflow.devflow.repository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.devflow.devflow.entity.AuditLog;

public interface AuditLogsRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    @EntityGraph(attributePaths = {"user"})
    List<AuditLog> findByIssueIdOrderByCreatedAtAscIdAsc(Long issueId);

    @EntityGraph(attributePaths = {"user", "issue"})
    @Query(value = "select log from AuditLog log where "
            + "(:actorId is null or log.user.id = :actorId) and "
            + "(:action is null or upper(log.action) = upper(:action)) and "
            + "(:issueId is null or log.issue.id = :issueId) and "
            + "(:outcome is null or (:outcome = 'FAILURE' and log.action = 'LOGIN_FAILED') "
            + "or (:outcome = 'SUCCESS' and log.action <> 'LOGIN_FAILED')) and "
            + "(:fromTime is null or log.createdAt >= :fromTime) and "
            + "(:toTime is null or log.createdAt <= :toTime)",
            countQuery = "select count(log) from AuditLog log where "
                    + "(:actorId is null or log.user.id = :actorId) and "
                    + "(:action is null or upper(log.action) = upper(:action)) and "
                    + "(:issueId is null or log.issue.id = :issueId) and "
                    + "(:outcome is null or (:outcome = 'FAILURE' and log.action = 'LOGIN_FAILED') "
                    + "or (:outcome = 'SUCCESS' and log.action <> 'LOGIN_FAILED')) and "
                    + "(:fromTime is null or log.createdAt >= :fromTime) and "
                    + "(:toTime is null or log.createdAt <= :toTime)")
    Page<AuditLog> searchAuditLogs(@Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("issueId") Long issueId,
            @Param("outcome") String outcome,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable);

    @EntityGraph(attributePaths = { "user", "issue" })
    @Query("select log from AuditLog log where log.issue.project.team.id = :teamId")
    List<AuditLog> findTeamActivity(@Param("teamId") Long teamId, Pageable pageable);

    /** Keep audit entries when an issue is removed, while releasing its foreign-key reference. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditLog log set log.issue = null where log.issue.id = :issueId")
    int detachIssueReference(@Param("issueId") Long issueId);
}
