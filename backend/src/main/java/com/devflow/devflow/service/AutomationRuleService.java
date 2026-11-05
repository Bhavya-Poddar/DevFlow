package com.devflow.devflow.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.AutomationExecutionResponseDTO;
import com.devflow.devflow.dto.AutomationRuleRequestDTO;
import com.devflow.devflow.dto.AutomationRuleResponseDTO;
import com.devflow.devflow.entity.AutomationActionType;
import com.devflow.devflow.entity.AutomationExecution;
import com.devflow.devflow.entity.AutomationRule;
import com.devflow.devflow.entity.AutomationTriggerType;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AutomationExecutionRepository;
import com.devflow.devflow.repository.AutomationRuleRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.IssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class AutomationRuleService {
    private final AutomationRuleRepository ruleRepository;
    private final AutomationExecutionRepository executionRepository;
    private final UserRepository userRepository;
    private final AuditLogServices auditLogService;
    private final IssueRepository issueRepository;
    private final ProjectAccessService projectAccessService;

    public AutomationRuleService(AutomationRuleRepository ruleRepository,
            AutomationExecutionRepository executionRepository, UserRepository userRepository,
            AuditLogServices auditLogService, IssueRepository issueRepository,
            ProjectAccessService projectAccessService) {
        this.ruleRepository = ruleRepository;
        this.executionRepository = executionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.issueRepository = issueRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public List<AutomationRuleResponseDTO> listRules(String actorEmail) {
        requireManager(actorEmail);
        return ruleRepository.findAllByDeletedFalseOrderByUpdatedAtDescIdDesc().stream()
            .map(AutomationRuleResponseDTO::new).toList();
    }

    @Transactional
    public AutomationRuleResponseDTO createRule(AutomationRuleRequestDTO request, String actorEmail) {
        User actor = requireManager(actorEmail);
        AutomationRule rule = applyRequest(new AutomationRule(), request);
        rule.setCreatedBy(actor);
        AutomationRule saved = ruleRepository.save(rule);
        auditLogService.createLog("AUTOMATION_RULE_CREATED",
            "targetType=AUTOMATION_RULE; targetId=" + saved.getId(), null, actor.getId());
        return new AutomationRuleResponseDTO(saved);
    }

    @Transactional
    public AutomationRuleResponseDTO updateRule(Long id, AutomationRuleRequestDTO request, String actorEmail) {
        User actor = requireManager(actorEmail);
        AutomationRule rule = getRule(id);
        // The built-in seed key is server-controlled and is never read from the request DTO.
        applyRequest(rule, request);
        AutomationRule saved = ruleRepository.save(rule);
        auditLogService.createLog("AUTOMATION_RULE_UPDATED",
            "targetType=AUTOMATION_RULE; targetId=" + saved.getId(), null, actor.getId());
        return new AutomationRuleResponseDTO(saved);
    }

    @Transactional
    public AutomationRuleResponseDTO setEnabled(Long id, boolean enabled, String actorEmail) {
        User actor = requireManager(actorEmail);
        AutomationRule rule = getRule(id);
        rule.setEnabled(enabled);
        AutomationRule saved = ruleRepository.save(rule);
        auditLogService.createLog(enabled ? "AUTOMATION_RULE_ENABLED" : "AUTOMATION_RULE_DISABLED",
            "targetType=AUTOMATION_RULE; targetId=" + id, null, actor.getId());
        return new AutomationRuleResponseDTO(saved);
    }

    @Transactional
    public void deleteRule(Long id, String actorEmail) {
        User actor = requireManager(actorEmail);
        AutomationRule rule = getRule(id);
        if (rule.getBuiltInKey() != null) {
            // Keep the unique seed key as a tombstone so startup initialization won't recreate it.
            rule.setDeleted(true);
            ruleRepository.save(rule);
        } else {
            ruleRepository.delete(rule);
        }
        auditLogService.createLog("AUTOMATION_RULE_DELETED",
            "targetType=AUTOMATION_RULE; targetId=" + id, null, actor.getId());
    }

    @Transactional(readOnly = true)
    public List<AutomationExecutionResponseDTO> recentExecutions(String actorEmail, int limit) {
        User actor = requireManager(actorEmail);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<AutomationExecution> executions = executionRepository.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            ;
        if (projectAccessService.isAdministrator(actor)) {
            return executions.stream().map(AutomationExecutionResponseDTO::new).toList();
        }
        java.util.Set<Long> targetIssueIds = executions.stream().map(AutomationExecution::getTargetIssueId)
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> visibleProjectIds = new java.util.HashSet<>(
            projectAccessService.visibleProjectIds(actorEmail));
        java.util.Set<Long> visibleIssueIds = issueRepository.findAllById(targetIssueIds).stream()
            .filter(issue -> issue.getProject() == null || visibleProjectIds.contains(issue.getProject().getId()))
            .map(com.devflow.devflow.entity.Issue::getId).collect(java.util.stream.Collectors.toSet());
        return executions.stream()
            .filter(execution -> execution.getTargetIssueId() == null
                || visibleIssueIds.contains(execution.getTargetIssueId()))
            .map(AutomationExecutionResponseDTO::new).toList();
    }

    @Transactional
    public void ensureDefaultRules() {
        addDefault("READY_FOR_TESTING_TESTER", "Ready for testing — notify tester",
            "Notify the assigned tester when a task enters the testing queue.",
            AutomationTriggerType.TASK_STATUS_CHANGED, IssueStatus.READY_FOR_TESTING,
            AutomationActionType.NOTIFY_ASSIGNED_TESTER);
        addDefault("TEST_FAILED_DEVELOPER", "Testing failed — notify developer",
            "Notify the assigned developer when a test fails.",
            AutomationTriggerType.TESTING_FAILED, null, AutomationActionType.NOTIFY_ASSIGNED_DEVELOPER);
        addDefault("BLOCKER_RESOLVED_OWNERS", "Blocker resolved — notify unblocked owners",
            "Notify owners when all blockers for their task are resolved.",
            AutomationTriggerType.BLOCKING_TASK_RESOLVED, null, AutomationActionType.NOTIFY_UNBLOCKED_OWNERS);
        addDefault("TEST_PASSED_DEVELOPER", "Testing passed — notify developer",
            "Notify the assigned developer when a test passes.",
            AutomationTriggerType.TESTING_PASSED, null, AutomationActionType.NOTIFY_ASSIGNED_DEVELOPER);
    }

    private void addDefault(String key, String name, String description, AutomationTriggerType trigger,
            IssueStatus conditionStatus, AutomationActionType action) {
        if (ruleRepository.existsByBuiltInKey(key)) return;
        AutomationRule rule = new AutomationRule();
        rule.setBuiltInKey(key);
        rule.setName(name);
        rule.setDescription(description);
        rule.setEnabled(true);
        rule.setTriggerEvent(trigger.name());
        rule.setConditionStatus(conditionStatus == null ? null : conditionStatus.name());
        rule.setActionType(action.name());
        ruleRepository.save(rule);
    }

    private AutomationRule applyRequest(AutomationRule rule, AutomationRuleRequestDTO request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule details are required");
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule name must be between 1 and 120 characters");
        }
        String description = request.getDescription() == null ? null : request.getDescription().trim();
        if (description != null && description.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule description cannot exceed 1000 characters");
        }
        AutomationTriggerType trigger = parseEnum(request.getTriggerEvent(), AutomationTriggerType.class, "Unsupported trigger event");
        AutomationActionType action = parseEnum(request.getActionType(), AutomationActionType.class, "Unsupported action type");
        String condition = null;
        if (request.getConditionStatus() != null && !request.getConditionStatus().isBlank()) {
            try {
                condition = IssueStatus.fromRequest(request.getConditionStatus()).name();
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Condition status is not supported");
            }
        }
        if (action == AutomationActionType.NOTIFY_UNBLOCKED_OWNERS
                && trigger != AutomationTriggerType.BLOCKING_TASK_RESOLVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Notify unblocked owners requires the blocker-resolved trigger");
        }
        rule.setName(name);
        rule.setDescription(description == null || description.isEmpty() ? null : description);
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        rule.setTriggerEvent(trigger.name());
        rule.setConditionStatus(condition);
        rule.setActionType(action.name());
        return rule;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String message) {
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private AutomationRule getRule(Long id) {
        return ruleRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation rule not found"));
    }

    private User requireManager(String email) {
        User actor = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is unavailable"));
        if (!actor.isEnabled() || !projectAccessService.hasGlobalPermission(email, "AUTOMATION_MANAGE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Global automation management permission is required");
        }
        return actor;
    }
}
