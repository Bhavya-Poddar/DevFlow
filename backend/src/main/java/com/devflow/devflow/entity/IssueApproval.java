package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "issue_approval", uniqueConstraints = @UniqueConstraint(
    name = "uk_issue_approval_cycle_stage", columnNames = {"issue_id", "workflow_cycle", "stage"}))
public class IssueApproval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issue_id", nullable = false) private Issue issue;
    @Column(name = "workflow_cycle", nullable = false) private int workflowCycle;
    @Column(nullable = false, length = 32) private String stage;
    @Column(nullable = false, length = 16) private String decision;
    @Column(columnDefinition = "TEXT") private String remark;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id", nullable = false) private User actor;
    @Column(name = "decided_at", nullable = false) private LocalDateTime decidedAt;

    protected IssueApproval() {}
    public IssueApproval(Issue issue, int workflowCycle, String stage, String decision, String remark, User actor, LocalDateTime decidedAt) {
        this.issue=issue; this.workflowCycle=workflowCycle; this.stage=stage; this.decision=decision;
        this.remark=remark; this.actor=actor; this.decidedAt=decidedAt;
    }
    public Long getId(){return id;} public Issue getIssue(){return issue;} public int getWorkflowCycle(){return workflowCycle;}
    public String getStage(){return stage;} public String getDecision(){return decision;} public String getRemark(){return remark;}
    public User getActor(){return actor;} public LocalDateTime getDecidedAt(){return decidedAt;}
}
