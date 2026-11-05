package com.devflow.devflow.dto;

import java.time.LocalDateTime;

import com.devflow.devflow.entity.AutomationRule;

public class AutomationRuleResponseDTO {
    private Long id;
    private String name;
    private String description;
    private boolean enabled;
    private String triggerEvent;
    private String conditionStatus;
    private String actionType;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AutomationRuleResponseDTO(AutomationRule rule) {
        id = rule.getId();
        name = rule.getName();
        description = rule.getDescription();
        enabled = rule.isEnabled();
        triggerEvent = rule.getTriggerEvent();
        conditionStatus = rule.getConditionStatus();
        actionType = rule.getActionType();
        createdById = rule.getCreatedBy() == null ? null : rule.getCreatedBy().getId();
        createdByName = rule.getCreatedBy() == null ? null : rule.getCreatedBy().getName();
        createdAt = rule.getCreatedAt();
        updatedAt = rule.getUpdatedAt();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public String getTriggerEvent() { return triggerEvent; }
    public String getConditionStatus() { return conditionStatus; }
    public String getActionType() { return actionType; }
    public Long getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
