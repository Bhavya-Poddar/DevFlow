package com.devflow.devflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.entity.AutomationEvent;
import com.devflow.devflow.entity.AutomationTriggerType;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.repository.AutomationEventRepository;
import com.devflow.devflow.service.AutomationEventService;

@ExtendWith(MockitoExtension.class)
class AutomationEventServiceTests {
    @Mock private AutomationEventRepository events;
    @InjectMocks private AutomationEventService service;

    @Test
    void duplicateSourceEventIsNotEnqueuedTwice() {
        Issue issue = new Issue();
        issue.setId(17L);
        when(events.existsBySourceKey("TESTING_PASSED:test-execution:42")).thenReturn(true);

        service.enqueue(AutomationTriggerType.TESTING_PASSED, issue,
            "TESTING", "RESOLVED", "test-execution:42");

        verify(events, never()).save(any(AutomationEvent.class));
    }

    @Test
    void firstOccurrenceIsPersistedWithSafeEventContext() {
        Issue issue = new Issue();
        issue.setId(17L);

        service.enqueue(AutomationTriggerType.TASK_STATUS_CHANGED, issue,
            "CODE_REVIEW", "READY_FOR_TESTING", "issue-update:17:operation-a");

        org.mockito.ArgumentCaptor<AutomationEvent> captor = org.mockito.ArgumentCaptor.forClass(AutomationEvent.class);
        verify(events).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("TASK_STATUS_CHANGED", captor.getValue().getTriggerEvent());
        org.junit.jupiter.api.Assertions.assertEquals(17L, captor.getValue().getIssueId());
        org.junit.jupiter.api.Assertions.assertEquals("CODE_REVIEW", captor.getValue().getPreviousStatus());
        org.junit.jupiter.api.Assertions.assertEquals("READY_FOR_TESTING", captor.getValue().getResultingStatus());
    }
}
