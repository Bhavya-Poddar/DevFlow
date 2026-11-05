package com.devflow.devflow.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AutomationDefaultsInitializer {
    private final AutomationRuleService ruleService;

    public AutomationDefaultsInitializer(AutomationRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureExamplesExist() {
        ruleService.ensureDefaultRules();
    }
}
