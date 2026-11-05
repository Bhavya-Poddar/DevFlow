package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.AutomationRuleRequestDTO;
import com.devflow.devflow.entity.AutomationRule;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AutomationExecutionRepository;
import com.devflow.devflow.repository.AutomationRuleRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.AutomationRuleService;
import com.devflow.devflow.service.ProjectAccessService;

@ExtendWith(MockitoExtension.class)
class AutomationRuleServiceTests {
    @Mock private AutomationRuleRepository rules;
    @Mock private AutomationExecutionRepository executions;
    @Mock private UserRepository users;
    @Mock private AuditLogServices auditLogs;
    @Mock private IssueRepository issues;
    @Mock private ProjectAccessService projectAccess;
    @InjectMocks private AutomationRuleService service;

    @Test
    void managerCanCreateValidatedRuleAndAuditUsesAuthenticatedActor() {
        User manager = user(12L, "PROJECT_MANAGER");
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager));
        when(projectAccess.hasGlobalPermission("manager@example.com", "AUTOMATION_MANAGE")).thenReturn(true);
        when(rules.save(any(AutomationRule.class))).thenAnswer(invocation -> {
            AutomationRule rule = invocation.getArgument(0);
            rule.setId(9L);
            return rule;
        });
        AutomationRuleRequestDTO request = request("TASK_STATUS_CHANGED", "READY_FOR_TESTING",
            "NOTIFY_ASSIGNED_TESTER");

        var response = service.createRule(request, "manager@example.com");

        assertEquals(9L, response.getId());
        assertEquals(12L, response.getCreatedById());
        assertEquals("READY_FOR_TESTING", response.getConditionStatus());
        verify(auditLogs).createLog("AUTOMATION_RULE_CREATED",
            "targetType=AUTOMATION_RULE; targetId=9", null, 12L);
    }

    @Test
    void arbitraryTriggersAndInvalidActionCombinationsAreRejected() {
        when(users.findByEmailIgnoreCase("admin@example.com"))
            .thenReturn(Optional.of(user(1L, "ADMIN")));
        when(projectAccess.hasGlobalPermission("admin@example.com", "AUTOMATION_MANAGE")).thenReturn(true);
        AutomationRuleRequestDTO unsupported = request("RUN_SCRIPT", null, "NOTIFY_ASSIGNED_DEVELOPER");
        ResponseStatusException triggerError = assertThrows(ResponseStatusException.class,
            () -> service.createRule(unsupported, "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, triggerError.getStatusCode());

        AutomationRuleRequestDTO invalidCombination = request("TESTING_FAILED", null, "NOTIFY_UNBLOCKED_OWNERS");
        ResponseStatusException actionError = assertThrows(ResponseStatusException.class,
            () -> service.createRule(invalidCombination, "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, actionError.getStatusCode());
    }

    @Test
    void disabledAndNonManagerAccountsCannotManageRules() {
        User disabled = user(2L, "ADMIN");
        disabled.setEnabled(false);
        when(users.findByEmailIgnoreCase("disabled@example.com")).thenReturn(Optional.of(disabled));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.listRules("disabled@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void ruleCanBeDisabledAndDeletedByManager() {
        User manager = user(12L, "PROJECT_MANAGER");
        AutomationRule rule = new AutomationRule();
        rule.setId(22L);
        rule.setName("Rule to disable");
        rule.setTriggerEvent("TESTING_FAILED");
        rule.setActionType("NOTIFY_ASSIGNED_DEVELOPER");
        rule.setEnabled(true);
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager));
        when(projectAccess.hasGlobalPermission("manager@example.com", "AUTOMATION_MANAGE")).thenReturn(true);
        when(rules.findByIdAndDeletedFalse(22L)).thenReturn(Optional.of(rule));
        when(rules.save(rule)).thenReturn(rule);

        var response = service.setEnabled(22L, false, "manager@example.com");
        service.deleteRule(22L, "manager@example.com");

        assertEquals(false, response.isEnabled());
        verify(rules).delete(rule);
        verify(auditLogs).createLog("AUTOMATION_RULE_DISABLED", "targetType=AUTOMATION_RULE; targetId=22", null, 12L);
        verify(auditLogs).createLog("AUTOMATION_RULE_DELETED", "targetType=AUTOMATION_RULE; targetId=22", null, 12L);
        verify(users, times(2)).findByEmailIgnoreCase("manager@example.com");
    }

    @Test
    void updatingRuleKeepsServerOwnedCreatorAndBuiltInKey() {
        User admin = user(1L, "ADMIN");
        User originalCreator = user(4L, "DEVELOPER");
        AutomationRule rule = new AutomationRule();
        rule.setId(30L);
        rule.setName("Built-in");
        rule.setTriggerEvent("TASK_STATUS_CHANGED");
        rule.setActionType("NOTIFY_ASSIGNED_TESTER");
        rule.setBuiltInKey("READY_FOR_TESTING_TESTER");
        rule.setCreatedBy(originalCreator);
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(projectAccess.hasGlobalPermission("admin@example.com", "AUTOMATION_MANAGE")).thenReturn(true);
        when(rules.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(rule));
        when(rules.save(rule)).thenReturn(rule);
        when(rules.existsByBuiltInKey(any(String.class))).thenReturn(true);
        AutomationRuleRequestDTO changed = request("TESTING_FAILED", null, "NOTIFY_ASSIGNED_DEVELOPER");
        changed.setName("Notify developer on failure");

        var response = service.updateRule(30L, changed, "admin@example.com");

        assertEquals("Notify developer on failure", response.getName());
        assertEquals("TESTING_FAILED", response.getTriggerEvent());
        assertEquals(4L, response.getCreatedById());
        assertEquals("READY_FOR_TESTING_TESTER", rule.getBuiltInKey());

        service.ensureDefaultRules();

        assertEquals("Notify developer on failure", rule.getName());
        assertEquals("TESTING_FAILED", rule.getTriggerEvent());
        verify(rules).save(rule);
    }

    @Test
    void initializesFourDefaultsOnceWhenMissing() {
        Set<String> persistedKeys = new HashSet<>();
        when(rules.existsByBuiltInKey(any(String.class)))
            .thenAnswer(invocation -> persistedKeys.contains(invocation.getArgument(0)));
        when(rules.save(any(AutomationRule.class))).thenAnswer(invocation -> {
            AutomationRule rule = invocation.getArgument(0);
            persistedKeys.add(rule.getBuiltInKey());
            return rule;
        });

        service.ensureDefaultRules();
        service.ensureDefaultRules();

        assertEquals(4, persistedKeys.size());
        verify(rules, times(4)).save(any(AutomationRule.class));
    }

    @Test
    void deletingBuiltInLeavesTombstoneAndInitializationDoesNotRecreateIt() {
        User admin = user(1L, "ADMIN");
        AutomationRule builtIn = new AutomationRule();
        builtIn.setId(41L);
        builtIn.setName("Built-in tester rule");
        builtIn.setBuiltInKey("READY_FOR_TESTING_TESTER");
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(projectAccess.hasGlobalPermission("admin@example.com", "AUTOMATION_MANAGE")).thenReturn(true);
        when(rules.findByIdAndDeletedFalse(41L)).thenReturn(Optional.of(builtIn));
        when(rules.existsByBuiltInKey(any(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return "READY_FOR_TESTING_TESTER".equals(key) && builtIn.isDeleted();
        });
        when(rules.save(any(AutomationRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteRule(41L, "admin@example.com");
        assertEquals(true, builtIn.isDeleted());

        service.ensureDefaultRules();

        org.mockito.ArgumentCaptor<AutomationRule> savedRules =
            org.mockito.ArgumentCaptor.forClass(AutomationRule.class);
        verify(rules, times(4)).save(savedRules.capture());
        assertEquals(1, savedRules.getAllValues().stream()
            .filter(rule -> "READY_FOR_TESTING_TESTER".equals(rule.getBuiltInKey())).count());
        verify(rules, org.mockito.Mockito.never()).delete(builtIn);
    }

    private AutomationRuleRequestDTO request(String trigger, String condition, String action) {
        AutomationRuleRequestDTO request = new AutomationRuleRequestDTO();
        request.setName("Notify tester");
        request.setDescription("A focused rule");
        request.setTriggerEvent(trigger);
        request.setConditionStatus(condition);
        request.setActionType(action);
        request.setEnabled(true);
        return request;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail(role.equals("PROJECT_MANAGER") ? "manager@example.com" : "admin@example.com");
        user.setRole(role);
        return user;
    }
}
