package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.AutomationExecution;

public class AutomationExecutionResponseDTO {
    private Long id;
    private Long eventId;
    private Long ruleId;
    private String ruleName;
    private String triggerEvent;
    private String actionType;
    private Long targetIssueId;
    private String outcome;
    private String summary;
    private LocalDateTime createdAt;

    public AutomationExecutionResponseDTO(AutomationExecution execution) {
        id = execution.getId();
        eventId = execution.getEvent().getId();
        ruleId = execution.getRule() == null ? null : execution.getRule().getId();
        ruleName = execution.getRuleName();
        triggerEvent = execution.getTriggerEvent();
        actionType = execution.getActionType();
        targetIssueId = execution.getTargetIssueId();
        outcome = execution.getOutcome();
        summary = execution.getSummary();
        createdAt = execution.getCreatedAt();
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public String getTriggerEvent() { return triggerEvent; }
    public String getActionType() { return actionType; }
    public Long getTargetIssueId() { return targetIssueId; }
    public String getOutcome() { return outcome; }
    public String getSummary() { return summary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
