package com.devflow.devflow.dto;
import java.time.LocalDateTime;
import com.devflow.devflow.entity.IssueApproval;
public record IssueApprovalResponseDTO(Long id, int workflowCycle, String stage, String decision, String remark,
        CollaboratorResponseDTO actor, LocalDateTime decidedAt) {
    public IssueApprovalResponseDTO(IssueApproval a){this(a.getId(),a.getWorkflowCycle(),a.getStage(),a.getDecision(),a.getRemark(),new CollaboratorResponseDTO(a.getActor()),a.getDecidedAt());}
}
