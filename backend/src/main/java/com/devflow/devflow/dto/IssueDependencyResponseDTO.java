package com.devflow.devflow.dto;

/** Safe summary for a related issue; status is read from the current issue row. */
public class IssueDependencyResponseDTO {
    private final Long issueId;
    private final String title;
    private final String status;
    private final String reason;

    public IssueDependencyResponseDTO(Long issueId, String title, String status, String reason) {
        this.issueId = issueId;
        this.title = title;
        this.status = status;
        this.reason = reason;
    }

    public Long getIssueId() { return issueId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
