package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "issue_maintenance", uniqueConstraints = @UniqueConstraint(name="uk_issue_maintenance_issue", columnNames="issue_id"))
public class IssueMaintenance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="issue_id", nullable=false) private Issue issue;
    @Column(name="started_at", nullable=false) private LocalDateTime startedAt;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(nullable=false, length=20) private String status="ACTIVE";
    @Column(name="completed_at") private LocalDateTime completedAt;
    protected IssueMaintenance() {}
    public IssueMaintenance(Issue issue, LocalDateTime startedAt, LocalDateTime expiresAt){this.issue=issue;this.startedAt=startedAt;this.expiresAt=expiresAt;}
    public Long getId(){return id;} public Issue getIssue(){return issue;} public LocalDateTime getStartedAt(){return startedAt;}
    public LocalDateTime getExpiresAt(){return expiresAt;} public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime completedAt){this.completedAt=completedAt;}
}
