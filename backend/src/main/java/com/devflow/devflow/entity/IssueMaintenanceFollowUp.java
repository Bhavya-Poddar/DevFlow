package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name="issue_maintenance_follow_up", uniqueConstraints=@UniqueConstraint(name="uk_maintenance_followup_issue", columnNames="follow_up_issue_id"))
public class IssueMaintenanceFollowUp {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="original_issue_id", nullable=false) private Issue originalIssue;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="follow_up_issue_id", nullable=false) private Issue followUpIssue;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="reporter_id", nullable=false) private User reporter;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="responsible_user_id", nullable=false) private User responsibleUser;
    @Column(nullable=false, columnDefinition="TEXT") private String reason;
    @Column(name="reported_at", nullable=false) private LocalDateTime reportedAt;
    protected IssueMaintenanceFollowUp() {}
    public IssueMaintenanceFollowUp(Issue originalIssue, Issue followUpIssue, User reporter, User responsibleUser, String reason, LocalDateTime reportedAt){
        this.originalIssue=originalIssue;this.followUpIssue=followUpIssue;this.reporter=reporter;this.responsibleUser=responsibleUser;this.reason=reason;this.reportedAt=reportedAt;
    }
    public Long getId(){return id;} public Issue getOriginalIssue(){return originalIssue;} public Issue getFollowUpIssue(){return followUpIssue;}
    public User getReporter(){return reporter;} public User getResponsibleUser(){return responsibleUser;} public String getReason(){return reason;} public LocalDateTime getReportedAt(){return reportedAt;}
}
