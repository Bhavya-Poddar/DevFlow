package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.AutomationActionType;
import com.devflow.devflow.entity.AutomationEvent;
import com.devflow.devflow.entity.AutomationExecution;
import com.devflow.devflow.entity.AutomationRule;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueDependency;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AutomationEventRepository;
import com.devflow.devflow.repository.AutomationExecutionRepository;
import com.devflow.devflow.repository.AutomationRuleRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.IssueRepository;

@Service
public class AutomationEventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AutomationEventProcessor.class);
    private static final int MAX_ATTEMPTS = 5;

    private final AutomationEventRepository eventRepository;
    private final AutomationRuleRepository ruleRepository;
    private final AutomationExecutionRepository executionRepository;
    private final IssueRepository issueRepository;
    private final IssueDependencyRepository dependencyRepository;
    private final NotificationService notificationService;

    public AutomationEventProcessor(AutomationEventRepository eventRepository,
            AutomationRuleRepository ruleRepository, AutomationExecutionRepository executionRepository,
            IssueRepository issueRepository, IssueDependencyRepository dependencyRepository,
            NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.ruleRepository = ruleRepository;
        this.executionRepository = executionRepository;
        this.issueRepository = issueRepository;
        this.dependencyRepository = dependencyRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void process(Long eventId) {
        AutomationEvent event = eventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !"PENDING".equals(event.getState())) return;

        Issue issue = event.getIssueId() == null ? null : issueRepository.findById(event.getIssueId()).orElse(null);
        List<AutomationRule> rules = ruleRepository
            .findByEnabledTrueAndDeletedFalseAndTriggerEventOrderByIdAsc(event.getTriggerEvent());
        for (AutomationRule rule : rules) {
            if (executionRepository.existsByEvent_IdAndRule_Id(event.getId(), rule.getId())) continue;
            String skipReason = null;
            if (issue == null) {
                skipReason = "Task is no longer available";
            } else if (rule.getConditionStatus() != null
                    && !Objects.equals(rule.getConditionStatus(), event.getResultingStatus())) {
                skipReason = "The configured status condition did not match";
            }

            List<Delivery> deliveries = skipReason == null ? resolveDeliveries(rule, event, issue) : List.of();
            if (skipReason == null && deliveries.isEmpty()) skipReason = "No eligible recipient was available";

            AutomationExecution execution = new AutomationExecution();
            execution.setEvent(event);
            execution.setRule(rule);
            execution.setRuleName(rule.getName());
            execution.setTriggerEvent(event.getTriggerEvent());
            execution.setActionType(rule.getActionType());
            execution.setTargetIssueId(issue == null ? event.getIssueId() : issue.getId());
            if (skipReason != null) {
                execution.setOutcome("SKIPPED");
                execution.setSummary(skipReason);
            } else {
                for (Delivery delivery : deliveries) {
                    notificationService.createNotification(delivery.recipient(), delivery.type(), delivery.message(),
                        delivery.targetPath());
                }
                execution.setOutcome("SUCCESS");
                execution.setSummary("Created " + deliveries.size() + " notification(s)");
            }
            executionRepository.save(execution);
        }

        event.setState("PROCESSED");
        event.setProcessedAt(LocalDateTime.now());
        event.setErrorSummary(null);
        eventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, String safeFailureType) {
        AutomationEvent event = eventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !"PENDING".equals(event.getState())) return;
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setErrorSummary("Processing failed (" + safeFailureType + ")");
        if (attempts >= MAX_ATTEMPTS) {
            event.setState("FAILED");
            event.setProcessedAt(LocalDateTime.now());
            if (!executionRepository.existsByEvent_IdAndRuleIsNullAndOutcome(eventId, "FAILED")) {
                AutomationExecution failure = new AutomationExecution();
                failure.setEvent(event);
                failure.setRuleName("Automation event processing");
                failure.setTriggerEvent(event.getTriggerEvent());
                failure.setActionType("PROCESS_EVENT");
                failure.setTargetIssueId(event.getIssueId());
                failure.setOutcome("FAILED");
                failure.setSummary(event.getErrorSummary() + " after " + attempts + " attempts");
                executionRepository.save(failure);
            }
            logger.error("Automation event processing permanently failed: eventId={}, issueId={}, failureType={}",
                eventId, event.getIssueId(), safeFailureType);
        } else {
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(60, 1L << attempts)));
            logger.warn("Automation event processing will retry: eventId={}, issueId={}, attempt={}, failureType={}",
                eventId, event.getIssueId(), attempts, safeFailureType);
        }
        eventRepository.save(event);
    }

    private List<Delivery> resolveDeliveries(AutomationRule rule, AutomationEvent event, Issue issue) {
        AutomationActionType action = AutomationActionType.valueOf(rule.getActionType());
        List<Delivery> deliveries = new ArrayList<>();
        switch (action) {
            case NOTIFY_ASSIGNED_DEVELOPER -> {
                User developer = issue.getAssignedUser();
                if (developer != null && "DEVELOPER".equals(developer.getRole())) {
                    addDelivery(deliveries, developer, notificationType(event), message(event, issue), issue);
                }
            }
            case NOTIFY_ASSIGNED_TESTER -> {
                User tester = issue.getTester();
                if (tester != null && "TESTER".equals(tester.getRole())) {
                    addDelivery(deliveries, tester, notificationType(event), message(event, issue), issue);
                }
            }
            case NOTIFY_UNBLOCKED_OWNERS -> {
                Map<String, Delivery> unique = new LinkedHashMap<>();
                for (IssueDependency dependency : dependencyRepository.findByBlockingIssue_Id(issue.getId())) {
                    Issue blocked = dependency.getBlockedIssue();
                    if (blocked == null || blocked.getId() == null || !isUnblocked(blocked)) continue;
                    User owner = blocked.getAssignedUser();
                    if (owner == null || !owner.isEnabled()) continue;
                    String key = owner.getId() + ":" + blocked.getId();
                    unique.putIfAbsent(key, new Delivery(owner, "ISSUE_UNBLOCKED",
                        "Task '" + issue.getTitle() + "' was resolved; task '" + blocked.getTitle() + "' is now unblocked",
                        NotificationTargetPaths.issue(blocked.getId())));
                }
                deliveries.addAll(unique.values());
            }
        }
        return deliveries;
    }

    private boolean isUnblocked(Issue blocked) {
        for (IssueDependency dependency : dependencyRepository.findByBlockedIssue_Id(blocked.getId())) {
            Issue blocker = dependency.getBlockingIssue();
            if (blocker != null && !"RESOLVED".equals(blocker.getStatus())) return false;
        }
        return true;
    }

    private void addDelivery(List<Delivery> deliveries, User recipient, String type, String message, Issue issue) {
        if (recipient == null || !recipient.isEnabled()) return;
        deliveries.add(new Delivery(recipient, type, message,
            NotificationTargetPaths.issue(issue.getId())));
    }

    private String notificationType(AutomationEvent event) {
        return switch (event.getTriggerEvent()) {
            case "TESTING_PASSED" -> "TEST_PASSED";
            case "TESTING_FAILED" -> "TEST_FAILED";
            case "TASK_STATUS_CHANGED" -> "READY_FOR_TESTING".equals(event.getResultingStatus())
                ? "READY_FOR_TESTING" : "ISSUE_STATUS_CHANGED";
            default -> "AUTOMATION_EVENT";
        };
    }

    private String message(AutomationEvent event, Issue issue) {
        return switch (event.getTriggerEvent()) {
            case "TESTING_PASSED" -> "Testing passed for task '" + issue.getTitle() + "'";
            case "TESTING_FAILED" -> "Testing failed for task '" + issue.getTitle() + "'";
            case "TASK_STATUS_CHANGED" -> "READY_FOR_TESTING".equals(event.getResultingStatus())
                ? "Task '" + issue.getTitle() + "' is ready for testing"
                : "Task '" + issue.getTitle() + "' changed status to " + event.getResultingStatus();
            default -> "Automation event for task '" + issue.getTitle() + "'";
        };
    }

    private record Delivery(User recipient, String type, String message, String targetPath) { }
}
