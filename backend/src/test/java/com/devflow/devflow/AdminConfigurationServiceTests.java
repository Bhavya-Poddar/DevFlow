package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.AdminMenuEntryRequest;
import com.devflow.devflow.entity.AdminWebsiteSetting;
import com.devflow.devflow.repository.AdminDropdownOptionRepository;
import com.devflow.devflow.repository.AdminMenuEntryRepository;
import com.devflow.devflow.repository.AdminWebsiteSettingRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.AutomationRuleRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AdminConfigurationService;
import com.devflow.devflow.service.EffectivePermissionService;

@ExtendWith(MockitoExtension.class)
class AdminConfigurationServiceTests {
    @Mock private AdminDropdownOptionRepository options;
    @Mock private AdminMenuEntryRepository menus;
    @Mock private AdminWebsiteSettingRepository settings;
    @Mock private EffectivePermissionService permissions;
    @Mock private IssueRepository issues;
    @Mock private ProjectRepository projects;
    @Mock private SprintRepository sprints;
    @Mock private TestExecutionRepository testExecutions;
    @Mock private AutomationRuleRepository automationRules;
    @Mock private UserRepository users;
    @InjectMocks private AdminConfigurationService service;

    @Test
    void adminConfigurationRejectsCallersWithoutProtectedRecoveryPermission() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
            .when(permissions).require("member@example.test", "ROLE_MANAGE", null, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.adminOptions("member@example.test"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(options);
    }

    @Test
    void menuEditingRejectsRoutesOutsideTheServerAllowlist() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.updateMenu("https://attacker.example", new AdminMenuEntryRequest("External", true, 1),
                "admin@example.test"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(permissions).require("admin@example.test", "ROLE_MANAGE", null, null);
        verifyNoInteractions(menus);
    }

    @Test
    void menuVisibilityAcceptsOnlySupportedGlobalPermissions() {
        var entry = new com.devflow.devflow.entity.AdminMenuEntry();
        entry.setRouteKey("dashboard"); entry.setDisplayLabel("Dashboard"); entry.setEnabled(true); entry.setSortOrder(10);
        when(menus.findById("dashboard")).thenReturn(Optional.of(entry));
        when(menus.saveAndFlush(entry)).thenReturn(entry);

        var saved = service.updateMenu("dashboard",
            new AdminMenuEntryRequest("Dashboard", true, 10, "USER_MANAGE"), "admin@example.test");
        assertEquals("USER_MANAGE", saved.requiredPermission());

        ResponseStatusException scoped = assertThrows(ResponseStatusException.class,
            () -> service.updateMenu("dashboard", new AdminMenuEntryRequest(null, null, null, "ISSUE_READ"),
                "admin@example.test"));
        ResponseStatusException protectedKey = assertThrows(ResponseStatusException.class,
            () -> service.updateMenu("dashboard", new AdminMenuEntryRequest(null, null, null, "ROLE_MANAGE"),
                "admin@example.test"));
        assertEquals(HttpStatus.BAD_REQUEST, scoped.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, protectedKey.getStatusCode());
    }

    @Test
    void publicWebsiteConfigurationNeverReturnsDeploymentSecretKeys() {
        AdminWebsiteSetting workspace = setting("workspace.name", "DevFlow");
        AdminWebsiteSetting secret = setting("jwt.secret", "must-not-be-returned");
        when(settings.findAll()).thenReturn(List.of(workspace, secret));

        var result = service.publicSettings();

        assertEquals(1, result.size());
        assertEquals("workspace.name", result.getFirst().key());
        assertTrue(result.stream().noneMatch(value -> value.key().contains("secret")));
    }

    @Test
    void unsupportedDropdownCategoryIsRejectedBeforeAnyRepositoryRead() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.publicOptions("NOT_A_REAL_CATEGORY"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(options);
    }

    @Test
    void catalogListsEveryRealOptionGroupAndKeepsWorkflowKeysInProtectedMode() {
        var catalog = service.adminDropdownCatalog("admin@example.test");

        assertEquals(20, catalog.size());
        assertTrue(catalog.stream().anyMatch(item -> item.category().equals("ISSUE_STATUS")
            && item.managementMode().equals("LABELS_ONLY")));
        assertTrue(catalog.stream().anyMatch(item -> item.category().equals("AUTOMATION_ACTION")
            && item.module().equals("Automations")));
        assertTrue(catalog.stream().anyMatch(item -> item.category().equals("PROJECT_CATEGORY")
            && item.managementMode().equals("FULL")));
        verify(permissions, org.mockito.Mockito.atLeastOnce()).require("admin@example.test", "ROLE_MANAGE", null, null);
    }

    @Test
    void fixedWorkflowValuesAllowLabelAndOrderEditsButCannotBeAddedDisabledOrDeleted() {
        var status = new com.devflow.devflow.entity.AdminDropdownOption();
        ReflectionTestUtils.setField(status, "id", 700L);
        status.setCategory("ISSUE_STATUS"); status.setCode("READY_FOR_TESTING");
        status.setDisplayLabel("Ready for testing"); status.setEnabled(true);
        when(options.findByIdForUpdate(700L)).thenReturn(Optional.of(status));
        when(options.saveAndFlush(status)).thenReturn(status);

        var renamed = service.updateOption(700L,
            new com.devflow.devflow.dto.AdminDropdownOptionRequest("READY_FOR_TESTING", "QA queue", null, 42),
            "admin@example.test");
        assertEquals("QA queue", renamed.displayLabel());
        assertEquals(42, renamed.sortOrder());
        assertTrue(renamed.enabled());

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.updateOption(700L,
                new com.devflow.devflow.dto.AdminDropdownOptionRequest(null, null, false, null), "admin@example.test"))
            .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.createOption("ISSUE_STATUS",
                new com.devflow.devflow.dto.AdminDropdownOptionRequest("CUSTOM", "Custom", true, 50), "admin@example.test"))
            .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.deleteOption(700L, "admin@example.test")).getStatusCode());
    }

    @Test
    void everyProtectedOptionGroupAllowsOnlyLabelAndOrderChanges() {
        List<String> protectedCategories=List.of("ISSUE_STATUS","PROJECT_STATUS","SPRINT_STATUS","TEST_RESULT",
            "AUTOMATION_TRIGGER","AUTOMATION_ACTION","ACCOUNT_STATUS","REPORTING_RANGE","WORKLOAD_LEVEL");
        long id=800;
        for(String category:protectedCategories){
            long optionId=id;
            var option=new com.devflow.devflow.entity.AdminDropdownOption();
            ReflectionTestUtils.setField(option,"id",optionId);
            option.setCategory(category);option.setCode("PROTECTED_VALUE");option.setDisplayLabel("Original");option.setEnabled(true);
            when(options.findByIdForUpdate(optionId)).thenReturn(Optional.of(option));
            when(options.saveAndFlush(option)).thenReturn(option);
            var saved=service.updateOption(optionId,new com.devflow.devflow.dto.AdminDropdownOptionRequest(
                "PROTECTED_VALUE","Configured label",true,55),"admin@example.test");
            assertEquals("Configured label",saved.displayLabel(),category);
            assertEquals(55,saved.sortOrder(),category);
            assertTrue(saved.enabled(),category);
            assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.deleteOption(optionId,"admin@example.test")).getStatusCode());
            id++;
        }
    }

    @Test
    void safeDeleteRequiresAnUnusedFullManagementOptionAndKeepsAnEnabledChoice() {
        var option = new com.devflow.devflow.entity.AdminDropdownOption();
        ReflectionTestUtils.setField(option, "id", 701L);
        option.setCategory("PRIORITY"); option.setCode("LOW"); option.setDisplayLabel("Low"); option.setEnabled(true);
        when(options.findByIdForUpdate(701L)).thenReturn(Optional.of(option));
        when(issues.countByPriority("LOW")).thenReturn(2L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.deleteOption(701L, "admin@example.test")).getStatusCode());

        when(issues.countByPriority("LOW")).thenReturn(0L);
        when(options.findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc("PRIORITY")).thenReturn(List.of(option));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.deleteOption(701L, "admin@example.test")).getStatusCode());
    }

    @Test
    void unusedBusinessOptionCanBeDeletedWhenOtherEnabledOptionsRemain() {
        var option=new com.devflow.devflow.entity.AdminDropdownOption();
        ReflectionTestUtils.setField(option,"id",702L);
        option.setCategory("PRIORITY");option.setCode("CUSTOM");option.setDisplayLabel("Custom");option.setEnabled(true);
        var remaining=new com.devflow.devflow.entity.AdminDropdownOption();
        ReflectionTestUtils.setField(remaining,"id",703L);
        remaining.setCategory("PRIORITY");remaining.setCode("HIGH");remaining.setDisplayLabel("High");remaining.setEnabled(true);
        when(options.findByIdForUpdate(702L)).thenReturn(Optional.of(option));
        when(issues.countByPriority("CUSTOM")).thenReturn(0L);
        when(options.findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc("PRIORITY")).thenReturn(List.of(option,remaining));

        service.deleteOption(702L,"admin@example.test");

        verify(options).delete(option);
    }

    @Test
    void projectCategoryOptionsAreSupportedAndReportExistingProjectUsage() {
        var request = new com.devflow.devflow.dto.AdminDropdownOptionRequest("INTERNAL", "Internal", true, 10);
        when(options.existsByCategoryAndCode("PROJECT_CATEGORY", "INTERNAL")).thenReturn(false);
        when(options.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(projects.countByCategory("INTERNAL")).thenReturn(3L);

        var created = service.createOption("PROJECT_CATEGORY", request, "admin@example.test");
        var publicOptions = service.publicOptions("PROJECT_CATEGORY");

        assertEquals("PROJECT_CATEGORY", created.category());
        assertEquals(3L, created.usageCount());
        assertTrue(publicOptions.isEmpty());
    }

    @Test
    void supportedDropdownCategoriesCanBeAddedRenamedReorderedDisabledAndReenabled() {
        for (String category : List.of("PRIORITY", "PROJECT_CATEGORY")) {
            String code = category.equals("PRIORITY") ? "URGENT" : "INTERNAL";
            Long id = category.equals("PRIORITY") ? 101L : 102L;
            var request = new com.devflow.devflow.dto.AdminDropdownOptionRequest(code, "Initial label", true, 10);
            when(options.existsByCategoryAndCode(category, code)).thenReturn(false);
            when(options.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var created = service.createOption(category, request, "admin@example.test");
            assertEquals(category, created.category());
            assertEquals(code, created.code());
            assertEquals(10, created.sortOrder());
            var savedOption = ArgumentCaptor.forClass(com.devflow.devflow.entity.AdminDropdownOption.class);
            verify(options, atLeastOnce()).saveAndFlush(savedOption.capture());
            ReflectionTestUtils.setField(savedOption.getValue(), "id", id);

            var existing = savedOption.getValue();
            when(options.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
            var sibling = new com.devflow.devflow.entity.AdminDropdownOption();
            ReflectionTestUtils.setField(sibling, "id", id + 1000);
            sibling.setCategory(category);
            sibling.setCode("OTHER");
            sibling.setDisplayLabel("Other");
            sibling.setEnabled(true);
            when(options.findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc(category)).thenReturn(List.of(sibling));

            var edited = service.updateOption(id,
                new com.devflow.devflow.dto.AdminDropdownOptionRequest(code, "Updated label", true, 30),
                "admin@example.test");
            assertEquals("Updated label", edited.displayLabel());
            assertEquals(30, edited.sortOrder());

            var disabled = service.updateOption(id,
                new com.devflow.devflow.dto.AdminDropdownOptionRequest(code, null, false, null),
                "admin@example.test");
            assertEquals(false, disabled.enabled());
            var reenabled = service.updateOption(id,
                new com.devflow.devflow.dto.AdminDropdownOptionRequest(code, null, true, null),
                "admin@example.test");
            assertEquals(true, reenabled.enabled());
            assertEquals("Updated label", reenabled.displayLabel());
            assertEquals(30, reenabled.sortOrder());
        }
    }

    @Test
    void disabledProjectCategoryCanRemainOnExistingProjectButCannotBeNewlyAssigned() {
        var option = new com.devflow.devflow.entity.AdminDropdownOption();
        option.setCategory("PROJECT_CATEGORY"); option.setCode("INTERNAL"); option.setDisplayLabel("Internal");
        option.setEnabled(false);
        when(options.findByCategoryAndCodeForUpdate("PROJECT_CATEGORY", "INTERNAL")).thenReturn(java.util.Optional.of(option));

        service.validateProjectCategory("internal", "INTERNAL");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.validateProjectCategory("INTERNAL", null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void disabledPriorityRemainsValidForExistingTasksButCannotBeAssignedToNewTasks() {
        var option = new com.devflow.devflow.entity.AdminDropdownOption();
        option.setCategory("PRIORITY"); option.setCode("URGENT"); option.setDisplayLabel("Urgent"); option.setEnabled(false);
        when(options.findByCategoryAndCodeForUpdate("PRIORITY", "URGENT")).thenReturn(Optional.of(option));

        service.validatePriority("urgent", "URGENT");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.validatePriority("URGENT", "HIGH"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private AdminWebsiteSetting setting(String key, String value) {
        AdminWebsiteSetting setting = new AdminWebsiteSetting();
        setting.setKey(key);
        setting.setValue(value);
        return setting;
    }
}
