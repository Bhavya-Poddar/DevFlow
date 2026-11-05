package com.devflow.devflow.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.IssueApproval;

public interface IssueApprovalRepository extends JpaRepository<IssueApproval, Long> {
    List<IssueApproval> findByIssueIdOrderByWorkflowCycleAscDecidedAtAscIdAsc(Long issueId);
    boolean existsByIssueIdAndWorkflowCycleAndStageAndDecision(Long issueId, int workflowCycle, String stage, String decision);
    java.util.Optional<IssueApproval> findFirstByIssueIdAndWorkflowCycleAndStageAndDecisionOrderByDecidedAtDescIdDesc(
            Long issueId, int workflowCycle, String stage, String decision);
}
