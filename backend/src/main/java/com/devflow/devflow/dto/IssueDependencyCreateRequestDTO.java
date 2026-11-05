package com.devflow.devflow.dto;

/** Client-selectable blocker and explanation; actor and timestamps are server controlled. */
public class IssueDependencyCreateRequestDTO {
    private Long blockingIssueId;
    private String reason;

    public Long getBlockingIssueId() { return blockingIssueId; }
    public void setBlockingIssueId(Long blockingIssueId) { this.blockingIssueId = blockingIssueId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
