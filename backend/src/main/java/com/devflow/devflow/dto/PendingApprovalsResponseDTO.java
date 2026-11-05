package com.devflow.devflow.dto;

import java.util.List;

public record PendingApprovalsResponseDTO(long teamLeadPendingCount, long projectManagerPendingCount,
        List<PendingApprovalItemDTO> pendingApprovals) {
    public PendingApprovalsResponseDTO {
        pendingApprovals = List.copyOf(pendingApprovals);
    }
}
