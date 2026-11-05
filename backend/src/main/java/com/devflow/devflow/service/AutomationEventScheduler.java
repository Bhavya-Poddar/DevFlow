package com.devflow.devflow.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.devflow.devflow.repository.AutomationEventRepository;

@Component
public class AutomationEventScheduler {
    private final AutomationEventRepository eventRepository;
    private final AutomationEventProcessor processor;

    public AutomationEventScheduler(AutomationEventRepository eventRepository, AutomationEventProcessor processor) {
        this.eventRepository = eventRepository;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${devflow.automation.poll-delay-ms:1000}", initialDelayString = "${devflow.automation.initial-delay-ms:1500}")
    public void processPendingEvents() {
        eventRepository.findByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                "PENDING", LocalDateTime.now(), PageRequest.of(0, 25))
            .stream().map(event -> event.getId()).forEach(id -> {
                try {
                    processor.process(id);
                } catch (RuntimeException exception) {
                    processor.recordFailure(id, exception.getClass().getSimpleName());
                }
            });
    }
}
