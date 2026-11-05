package com.devflow.devflow.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.devflow.devflow.dto.*;
import com.devflow.devflow.entity.*;
import com.devflow.devflow.repository.*;

@Service
public class AdminConfigurationService {
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private record CategoryDefinition(String code, String module, String name, String description, String mode) {}
    private static final List<CategoryDefinition> CATEGORY_DEFINITIONS = List.of(
        new CategoryDefinition("PROJECT_CATEGORY", "Projects", "Project category", "Organize projects by business area.", "FULL"),
        new CategoryDefinition("PROJECT_STATUS", "Projects", "Project status", "Display labels only; project state codes and behavior stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("PRIORITY", "Tasks", "Task priority", "Prioritize tasks and code reviews.", "FULL"),
        new CategoryDefinition("ISSUE_STATUS", "Tasks", "Task status", "Display labels only; workflow identifiers and transitions stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("SPRINT_STATUS", "Sprints", "Sprint status", "Display labels only; sprint state codes stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("TEST_RESULT", "Testing", "Test result", "Display labels only; pass/fail outcomes stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("AUTOMATION_TRIGGER", "Automations", "Automation trigger", "Display labels only; supported event keys stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("AUTOMATION_ACTION", "Automations", "Automation action", "Display labels only; supported action keys stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("ACCOUNT_STATUS", "Users", "Account status", "Display labels only; account activation remains security-controlled.", "LABELS_ONLY"),
        new CategoryDefinition("REPORTING_RANGE", "Reports", "Reporting range", "Display labels only; range values and calculations stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("WORKLOAD_LEVEL", "Reports", "Workload level", "Display labels only; workload thresholds stay fixed.", "LABELS_ONLY"),
        new CategoryDefinition("USER_RECORD", "Users and Teams", "People and role membership", "Options are registered accounts filtered by context; manage accounts and roles in their existing sections.", "RECORD_SELECTOR"),
        new CategoryDefinition("PROJECT_RECORD", "Projects", "Project selector", "Options are projects the signed-in user may access; manage project records in Projects.", "RECORD_SELECTOR"),
        new CategoryDefinition("TEAM_RECORD", "Teams", "Team selector", "Options are existing teams; manage memberships in Private Teams.", "RECORD_SELECTOR"),
        new CategoryDefinition("SPRINT_RECORD", "Sprints", "Sprint selector", "Options are sprints belonging to the selected project; manage sprint records in Sprints.", "RECORD_SELECTOR"),
        new CategoryDefinition("TASK_RECORD", "Tasks and Testing", "Task selector", "Options are existing authorized tasks; manage task records in Tasks.", "RECORD_SELECTOR"),
        new CategoryDefinition("ROLE_RECORD", "Users and Administration", "Role selector", "Options are existing roles; manage them in Manage Roles.", "RECORD_SELECTOR"),
        new CategoryDefinition("ROLE_SCOPE", "Administration", "Role scope", "Security scopes are fixed authorization identifiers and are not dropdown options.", "PROTECTED"),
        new CategoryDefinition("PERMISSION_KEY", "Administration", "Permission selector", "Permission keys come from the backend permission catalogue and remain protected.", "PROTECTED"),
        new CategoryDefinition("AUDIT_ACTION", "Audit Logs", "Audit action filter", "Options are action values from existing audit records; they are event data, not business configuration.", "DATA_DERIVED")
    );
    private static final Set<String> DROPDOWN_CATEGORIES = CATEGORY_DEFINITIONS.stream()
        .filter(definition -> Set.of("FULL", "LABELS_ONLY").contains(definition.mode()))
        .map(CategoryDefinition::code).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> FULL_MANAGEMENT_CATEGORIES = Set.of("PRIORITY", "PROJECT_CATEGORY");
    private static final Map<String, String> MENU_ALLOWLIST = Map.ofEntries(
        Map.entry("dashboard", "/dashboard"), Map.entry("users", "/team"), Map.entry("projects", "/projects"),
        Map.entry("private-teams", "/private-teams"), Map.entry("tasks", "/issues"), Map.entry("sprints", "/sprints"),
        Map.entry("testing", "/testing"), Map.entry("my-tests", "/my-tests"), Map.entry("code-reviews", "/code-reviews"),
        Map.entry("comments", "/comments"), Map.entry("reports", "/reports"), Map.entry("activity", "/activity"),
        Map.entry("audit-logs", "/audit-logs"), Map.entry("notifications", "/notifications"),
        Map.entry("automations", "/automations"), Map.entry("settings", "/settings"));
    private static final Set<String> WEBSITE_KEYS = Set.of("workspace.name", "workspace.tagline", "maintenance.period.days");
    private final AdminDropdownOptionRepository options;
    private final AdminMenuEntryRepository menus;
    private final AdminWebsiteSettingRepository settings;
    private final EffectivePermissionService permissions;
    private final IssueRepository issues;
    private final ProjectRepository projects;
    private final SprintRepository sprints;
    private final TestExecutionRepository testExecutions;
    private final AutomationRuleRepository automationRules;
    private final UserRepository users;

    public AdminConfigurationService(AdminDropdownOptionRepository options, AdminMenuEntryRepository menus,
            AdminWebsiteSettingRepository settings, EffectivePermissionService permissions, IssueRepository issues,
            ProjectRepository projects, SprintRepository sprints, TestExecutionRepository testExecutions,
            AutomationRuleRepository automationRules, UserRepository users) {
        this.options=options; this.menus=menus; this.settings=settings; this.permissions=permissions;
        this.issues=issues; this.projects=projects; this.sprints=sprints; this.testExecutions=testExecutions;
        this.automationRules=automationRules; this.users=users;
    }

    @Transactional(readOnly=true)
    public List<AdminDropdownOptionDTO> publicOptions(String category) {
        String normalized = category(category);
        return options.findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc(normalized).stream()
            .map(this::optionDto).toList();
    }
    @Transactional(readOnly=true)
    public List<AdminDropdownOptionDTO> adminOptions(String actor) {
        requireAdmin(actor);
        return options.findAll().stream().sorted(java.util.Comparator.comparing(AdminDropdownOption::getCategory)
                .thenComparingInt(AdminDropdownOption::getSortOrder).thenComparing(AdminDropdownOption::getCode))
            .map(this::optionDto).toList();
    }
    @Transactional(readOnly=true)
    public List<AdminDropdownCategoryDTO> adminDropdownCatalog(String actor) {
        requireAdmin(actor);
        Map<String, List<AdminDropdownOptionDTO>> grouped = adminOptions(actor).stream()
            .collect(java.util.stream.Collectors.groupingBy(AdminDropdownOptionDTO::category));
        return CATEGORY_DEFINITIONS.stream().map(definition -> new AdminDropdownCategoryDTO(
            definition.code(), definition.module(), definition.name(), definition.description(), definition.mode(),
            grouped.getOrDefault(definition.code(), List.of()))).toList();
    }
    @Transactional
    public AdminDropdownOptionDTO createOption(String category, AdminDropdownOptionRequest request, String actor) {
        requireAdmin(actor); String group=category(category);
        if (!FULL_MANAGEMENT_CATEGORIES.contains(group)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This list has protected values; only its display labels can be changed");
        }
        if (request==null) badRequest("Dropdown option is required");
        String code=request.code()==null?"":request.code().trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) badRequest("Option code must use uppercase letters, digits, or underscores");
        if (options.existsByCategoryAndCode(group,code)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Option code already exists");
        AdminDropdownOption option=new AdminDropdownOption(); option.setCategory(group); option.setCode(code);
        option.setDisplayLabel(label(request.displayLabel())); option.setSortOrder(order(request.sortOrder()));
        option.setEnabled(request.enabled()==null || request.enabled());
        return optionDto(options.saveAndFlush(option));
    }
    @Transactional
    public AdminDropdownOptionDTO updateOption(Long id, AdminDropdownOptionRequest request, String actor) {
        requireAdmin(actor); if(request==null) badRequest("Dropdown option is required");
        AdminDropdownOption option=options.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Option not found"));
        boolean fullManagement = FULL_MANAGEMENT_CATEGORIES.contains(option.getCategory());
        if(request.code()!=null && !request.code().equals(option.getCode())) badRequest("Option identifiers are immutable");
        if(request.displayLabel()!=null) option.setDisplayLabel(label(request.displayLabel()));
        if(request.enabled()!=null) {
            if (!fullManagement && !request.enabled()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required workflow and system values cannot be disabled");
            }
            if (fullManagement) option.setEnabled(request.enabled());
        }
        if(request.sortOrder()!=null) option.setSortOrder(order(request.sortOrder()));
        if(fullManagement && !option.isEnabled() && options.findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc(option.getCategory()).stream()
                .filter(item->!item.getId().equals(id)).findAny().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"At least one option in the category must remain enabled");
        }
        return optionDto(options.saveAndFlush(option));
    }
    @Transactional
    public void deleteOption(Long id, String actor) {
        requireAdmin(actor);
        AdminDropdownOption option=options.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Option not found"));
        if (!FULL_MANAGEMENT_CATEGORIES.contains(option.getCategory())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Protected workflow and system values cannot be deleted");
        }
        long uses = usageCount(option);
        if (uses > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This option is used by " + uses + " existing records; disable it to prevent new assignments");
        if (option.isEnabled()
                && options.findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc(option.getCategory()).size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one enabled option must remain in this list");
        }
        options.delete(option);
    }
    @Transactional(readOnly=true)
    public List<AdminMenuEntryDTO> publicMenu() { return menus.findAllByEnabledTrueOrderBySortOrderAscRouteKeyAsc().stream().map(this::menuDto).toList(); }
    @Transactional(readOnly=true)
    public List<AdminMenuEntryDTO> adminMenu(String actor) { requireAdmin(actor); return menus.findAllByOrderBySortOrderAscRouteKeyAsc().stream().map(this::menuDto).toList(); }
    @Transactional
    public AdminMenuEntryDTO updateMenu(String routeKey, AdminMenuEntryRequest request, String actor) {
        requireAdmin(actor); if(!MENU_ALLOWLIST.containsKey(routeKey)) badRequest("Navigation route is not supported");
        if(request==null) badRequest("Menu entry is required");
        AdminMenuEntry entry=menus.findById(routeKey).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu entry not found"));
        if(request.displayLabel()!=null) entry.setDisplayLabel(label(request.displayLabel()));
        if(request.enabled()!=null) entry.setEnabled(request.enabled());
        if(request.sortOrder()!=null) entry.setSortOrder(order(request.sortOrder()));
        if(request.requiredPermission()!=null) {
            String key=request.requiredPermission().trim();
            if(key.isEmpty()) entry.setRequiredPermission(null);
            else if(PermissionCatalogue.entries().stream().noneMatch(permission -> permission.key().equals(key)
                    && permission.scope()==com.devflow.devflow.entity.PermissionScope.GLOBAL)) {
                badRequest("Menu visibility requires a supported global permission");
            } else entry.setRequiredPermission(key);
        }
        return menuDto(menus.saveAndFlush(entry));
    }
    @Transactional(readOnly=true)
    public List<AdminWebsiteSettingDTO> publicSettings() { return settings.findAll().stream().filter(s->WEBSITE_KEYS.contains(s.getKey()))
        .sorted(java.util.Comparator.comparing(AdminWebsiteSetting::getKey)).map(this::settingDto).toList(); }
    @Transactional
    public AdminWebsiteSettingDTO updateWebsiteSetting(String key, AdminWebsiteSettingRequest request, String actor) {
        requireAdmin(actor); if(!WEBSITE_KEYS.contains(key)) badRequest("Website setting is not editable");
        if(request==null || request.value()==null || request.value().isBlank() || request.value().trim().length()>240) badRequest("Setting value is required and must be at most 240 characters");
        if ("maintenance.period.days".equals(key)) {
            try { int days=Integer.parseInt(request.value().trim()); if(days<1||days>365) throw new NumberFormatException(); }
            catch(NumberFormatException exception) { badRequest("Maintenance period must be a whole number between 1 and 365 days"); }
        }
        AdminWebsiteSetting setting=settings.findById(key).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Setting not found"));
        setting.setValue(request.value().trim()); return settingDto(settings.saveAndFlush(setting));
    }
    @Transactional(readOnly=true)
    public List<String> statuses() { return java.util.Arrays.stream(IssueStatus.values()).map(Enum::name).toList(); }
    @Transactional(readOnly=true)
    public long usageCount(AdminDropdownOption option) {
        return switch (option.getCategory()) {
            case "PRIORITY" -> issues.countByPriority(option.getCode());
            case "PROJECT_CATEGORY" -> projects.countByCategory(option.getCode());
            case "ISSUE_STATUS" -> issues.countByStatus(option.getCode());
            case "PROJECT_STATUS" -> projects.countByStatus(option.getCode());
            case "SPRINT_STATUS" -> sprints.countByStatusIgnoreCase(option.getCode());
            case "TEST_RESULT" -> testExecutions.countByResultIgnoreCase(option.getCode());
            case "AUTOMATION_TRIGGER" -> automationRules.countByTriggerEventIgnoreCase(option.getCode());
            case "AUTOMATION_ACTION" -> automationRules.countByActionTypeIgnoreCase(option.getCode());
            case "ACCOUNT_STATUS" -> "ACTIVE".equals(option.getCode()) ? users.countByEnabledTrue()
                : "INACTIVE".equals(option.getCode()) ? users.countByEnabledFalse() : 0;
            default -> 0;
        };
    }
    @Transactional
    public void validatePriority(String value, String existingValue) {
        if(value==null || value.isBlank()) return;
        AdminDropdownOption option=options.findByCategoryAndCodeForUpdate("PRIORITY",value.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Priority is not supported"));
        if(!option.isEnabled() && !option.getCode().equals(existingValue)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Priority is disabled");
    }
    @Transactional
    public void validateProjectCategory(String value, String existingValue) {
        if(value==null || value.isBlank()) return;
        AdminDropdownOption option=options.findByCategoryAndCodeForUpdate("PROJECT_CATEGORY",value.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Project category is not supported"));
        if(!option.isEnabled() && !option.getCode().equals(existingValue))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Project category is disabled");
    }
    public static Map<String,String> menuAllowlist(){return MENU_ALLOWLIST;}
    private AdminDropdownOptionDTO optionDto(AdminDropdownOption o){return new AdminDropdownOptionDTO(o.getId(),o.getCategory(),o.getCode(),o.getDisplayLabel(),o.isEnabled(),o.getSortOrder(),usageCount(o),usageTracked(o));}
    private boolean usageTracked(AdminDropdownOption option){return !Set.of("REPORTING_RANGE","WORKLOAD_LEVEL").contains(option.getCategory());}
    private AdminMenuEntryDTO menuDto(AdminMenuEntry e){return new AdminMenuEntryDTO(e.getRouteKey(),e.getDisplayLabel(),e.isEnabled(),e.getSortOrder(),e.getUpdatedAt(),e.getRequiredPermission());}
    private AdminWebsiteSettingDTO settingDto(AdminWebsiteSetting e){return new AdminWebsiteSettingDTO(e.getKey(),e.getValue(),e.getUpdatedAt());}
    private String category(String value){String c=value==null?"":value.trim().toUpperCase(Locale.ROOT); if(!DROPDOWN_CATEGORIES.contains(c))badRequest("Dropdown category is not supported"); return c;}
    private String label(String v){if(v==null||v.isBlank()||v.trim().length()>120)badRequest("Label is required and must be at most 120 characters");return v.trim();}
    private int order(Integer v){if(v==null)return 0;if(v < 0 || v > 100000)badRequest("Sort order is out of range");return v;}
    private void requireAdmin(String actor){permissions.require(actor,"ROLE_MANAGE",null,null);}
    private void badRequest(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
