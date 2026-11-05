package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.TestExecution;

public interface TestExecutionRepository extends JpaRepository<TestExecution, Long> {
    boolean existsByIssueIdAndWorkflowCycleAndResultAndStatus(Long issueId, int workflowCycle, String result, String status);
    long countByResultIgnoreCase(String result);
    List<TestExecution> findByIssueIdOrderByCreatedAtDesc(Long issueId);
    Optional<TestExecution> findFirstByIssueIdAndWorkflowCycleAndResultAndStatusOrderByCompletedAtDescIdDesc(
            Long issueId, int workflowCycle, String result, String status);
    boolean existsByIssueId(Long issueId);
    boolean existsByIssueIdAndStatus(Long issueId, String status);
    long countByIssueIdAndStatus(Long issueId, String status);
    Optional<TestExecution> findFirstByIssueIdAndTesterIdAndStatusOrderByCreatedAtDesc(
            Long issueId, Long testerId, String status);

    @Query("select upper(execution.result), count(execution) from TestExecution execution "
        + "join execution.issue issue where upper(execution.result) in ('PASSED', 'FAILED') "
        + "and (:projectId is null or issue.project.id = :projectId) "
        + "and (:sprintId is null or issue.sprint.id = :sprintId) group by upper(execution.result)")
    List<Object[]> dashboardCompletedResults(@Param("projectId") Long projectId,
        @Param("sprintId") Long sprintId);
}
