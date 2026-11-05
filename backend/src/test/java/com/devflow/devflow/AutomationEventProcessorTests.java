package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.entity.AutomationEvent;
import com.devflow.devflow.entity.AutomationExecution;
import com.devflow.devflow.entity.AutomationRule;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueDependency;
import com.devflow.devflow.entity.Notification;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AutomationEventRepository;
import com.devflow.devflow.repository.AutomationExecutionRepository;
import com.devflow.devflow.repository.AutomationRuleRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.service.AutomationEventProcessor;
import com.devflow.devflow.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class AutomationEventProcessorTests {
    @Mock private AutomationEventRepository events;
    @Mock private AutomationRuleRepository rules;
    @Mock private AutomationExecutionRepository executions;
    @Mock private IssueRepository issues;
    @Mock private IssueDependencyRepository dependencies;
    @Mock private NotificationService notifications;
    @InjectMocks private AutomationEventProcessor processor;

    private AutomationEvent event;
    private Issue task;
    private AutomationRule rule;

    @BeforeEach
    void setup() {
        event = new AutomationEvent();
        event.setId(4L);
        event.setIssueId(15L);
        event.setState("PENDING");
        event.setTriggerEvent("TASK_STATUS_CHANGED");
        event.setResultingStatus("READY_FOR_TESTING");
        task = new Issue();
        task.setId(15L);
        task.setTitle("Checkout flow");
        task.setStatus("READY_FOR_TESTING");
        task.setTester(user(2L, "TESTER"));
        rule = rule("TASK_STATUS_CHANGED", "READY_FOR_TESTING", "NOTIFY_ASSIGNED_TESTER");
        when(events.findByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(issues.findById(15L)).thenReturn(Optional.of(task));
        Mockito.lenient().when(rules.findByEnabledTrueAndDeletedFalseAndTriggerEventOrderByIdAsc("TASK_STATUS_CHANGED"))
            .thenReturn(List.of(rule));
        when(executions.existsByEvent_IdAndRule_Id(4L, 7L)).thenReturn(false);
        Mockito.lenient().when(notifications.createNotification(any(User.class), any(String.class), any(String.class), any(String.class)))
            .thenReturn(new Notification());
    }

    @Test
    void matchingStatusEventNotifiesAssignedTesterAndRecordsSuccessOnce() {
        processor.process(4L);
        processor.process(4L);

        verify(notifications).createNotification(task.getTester(), "READY_FOR_TESTING",
            "Task 'Checkout flow' is ready for testing", "/issues?issueId=15");
        verify(executions).save(any(AutomationExecution.class));
        assertEquals("PROCESSED", event.getState());
    }

    @Test
    void nonmatchingConditionIsRecordedAsSkippedWithoutNotification() {
        rule.setConditionStatus("IN_PROGRESS");
        processor.process(4L);
        verify(notifications, never()).createNotification(any(), any(), any(), any());
        org.mockito.ArgumentCaptor<AutomationExecution> captor =
            org.mockito.ArgumentCaptor.forClass(AutomationExecution.class);
        verify(executions).save(captor.capture());
        assertEquals("SKIPPED", captor.getValue().getOutcome());
    }

    @Test
    void resolvedBlockerNotifiesOwnerOnlyWhenAllBlockersAreResolved() {
        event.setTriggerEvent("BLOCKING_TASK_RESOLVED");
        event.setResultingStatus("RESOLVED");
        rule = rule("BLOCKING_TASK_RESOLVED", null, "NOTIFY_UNBLOCKED_OWNERS");
        rule.setId(7L);
        when(rules.findByEnabledTrueAndDeletedFalseAndTriggerEventOrderByIdAsc("BLOCKING_TASK_RESOLVED"))
            .thenReturn(List.of(rule));
        Issue blocked = new Issue();
        blocked.setId(31L);
        blocked.setTitle("Finish UI");
        blocked.setAssignedUser(user(3L, "DEVELOPER"));
        IssueDependency link = new IssueDependency();
        link.setBlockedIssue(blocked);
        link.setBlockingIssue(task);
        task.setStatus("RESOLVED");
        when(dependencies.findByBlockingIssue_Id(15L)).thenReturn(List.of(link));
        when(dependencies.findByBlockedIssue_Id(31L)).thenReturn(List.of(link));

        processor.process(4L);

        verify(notifications).createNotification(blocked.getAssignedUser(), "ISSUE_UNBLOCKED",
            "Task 'Checkout flow' was resolved; task 'Finish UI' is now unblocked", "/issues?issueId=31");
    }

    @Test
    void notificationFailurePropagatesAndIsRecordedAfterRetryLimit() {
        when(notifications.createNotification(any(), any(), any(), any())).thenThrow(new IllegalStateException("DB detail"));
        assertThrows(IllegalStateException.class, () -> processor.process(4L));
        verify(executions, never()).save(any(AutomationExecution.class));
        for (int i = 0; i < 5; i++) processor.recordFailure(4L, "IllegalStateException");
        assertEquals("FAILED", event.getState());
        verify(executions).save(any(AutomationExecution.class));
    }

    private AutomationRule rule(String trigger, String condition, String action) {
        AutomationRule value = new AutomationRule();
        value.setId(7L);
        value.setName("Test rule");
        value.setTriggerEvent(trigger);
        value.setConditionStatus(condition);
        value.setActionType(action);
        return value;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setName("Recipient " + id);
        user.setEmail("recipient" + id + "@example.com");
        user.setRole(role);
        return user;
    }
}
