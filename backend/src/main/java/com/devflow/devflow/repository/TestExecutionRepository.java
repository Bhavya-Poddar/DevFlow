package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.TestExecution;

public interface TestExecutionRepository extends JpaRepository<TestExecution, Long> {
    List<TestExecution> findByIssueIdOrderByCreatedAtDesc(Long issueId);
    boolean existsByIssueId(Long issueId);
    boolean existsByIssueIdAndStatus(Long issueId, String status);
    long countByIssueIdAndStatus(Long issueId, String status);
    Optional<TestExecution> findFirstByIssueIdAndTesterIdAndStatusOrderByCreatedAtDesc(
            Long issueId, Long testerId, String status);
}
