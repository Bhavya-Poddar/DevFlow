package com.devflow.devflow.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.AutomationEvent;
import com.devflow.devflow.entity.AutomationTriggerType;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.repository.AutomationEventRepository;

/** Writes a durable event to the same transaction as the business change that produced it. */
@Service
public class AutomationEventService {
    private final AutomationEventRepository eventRepository;

    public AutomationEventService(AutomationEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void enqueue(AutomationTriggerType trigger, Issue issue, String previousStatus,
            String resultingStatus, String operationKey) {
        AutomationEvent event = new AutomationEvent();
        String sourceKey = trigger.name() + ":" + operationKey;
        if (eventRepository.existsBySourceKey(sourceKey)) return;
        event.setSourceKey(sourceKey);
        event.setTriggerEvent(trigger.name());
        event.setIssueId(issue == null ? null : issue.getId());
        event.setPreviousStatus(previousStatus);
        event.setResultingStatus(resultingStatus);
        event.setOccurredAt(LocalDateTime.now());
        eventRepository.save(event);
    }
}
