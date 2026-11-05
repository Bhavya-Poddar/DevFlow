package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "automation_execution", uniqueConstraints =
    @UniqueConstraint(name = "uk_automation_execution_event_rule", columnNames = { "event_id", "rule_id" }))
public class AutomationExecution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private AutomationEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AutomationRule rule;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Column(name = "trigger_event", nullable = false, length = 40)
    private String triggerEvent;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "target_issue_id")
    private Long targetIssueId;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(length = 500)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void beforeCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AutomationEvent getEvent() { return event; }
    public void setEvent(AutomationEvent event) { this.event = event; }
    public AutomationRule getRule() { return rule; }
    public void setRule(AutomationRule rule) { this.rule = rule; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Long getTargetIssueId() { return targetIssueId; }
    public void setTargetIssueId(Long targetIssueId) { this.targetIssueId = targetIssueId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
