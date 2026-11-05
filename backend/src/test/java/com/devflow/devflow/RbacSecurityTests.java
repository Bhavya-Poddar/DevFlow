package com.devflow.devflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import static org.mockito.ArgumentMatchers.anyInt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.config.CorsConfig;
import com.devflow.devflow.controller.AuditLogController;
import com.devflow.devflow.controller.AutomationController;
import com.devflow.devflow.controller.CommentController;
import com.devflow.devflow.controller.IssueController;
import com.devflow.devflow.controller.AdminConfigurationController;
import com.devflow.devflow.controller.ProjectController;
import com.devflow.devflow.controller.ProjectReportController;
import com.devflow.devflow.controller.SprintController;
import com.devflow.devflow.controller.TestingController;
import com.devflow.devflow.controller.UserController;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.security.JwtAuthenticationFilter;
import com.devflow.devflow.security.CurrentUserAuthenticationService;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.security.SecurityConfig;
import com.devflow.devflow.security.ProjectPrivacyInterceptor;
import com.devflow.devflow.security.RequestPermissionAuthorizationManager;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.AutomationRuleService;
import com.devflow.devflow.service.AutomationEventService;
import com.devflow.devflow.service.CommentService;
import com.devflow.devflow.service.IssueService;
import com.devflow.devflow.service.TaskActivityTimelineService;
import com.devflow.devflow.dto.UserMembershipsResponseDTO;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.ProjectService;
import com.devflow.devflow.service.ProjectAccessService;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.service.RequestPermissionService;
import com.devflow.devflow.service.PermissionCatalogue;
import com.devflow.devflow.service.ProjectReportService;
import com.devflow.devflow.service.SprintBurndownSnapshotService;
import com.devflow.devflow.service.SprintService;
import com.devflow.devflow.service.TestExecutionService;
import com.devflow.devflow.service.IssueApprovalService;
import com.devflow.devflow.service.IssueMaintenanceService;
import com.devflow.devflow.service.ProjectAssignableMemberService;
import com.devflow.devflow.service.UserService;
import com.devflow.devflow.service.UserMembershipService;
import com.devflow.devflow.service.AdminConfigurationService;
import com.devflow.devflow.dto.AdminDropdownOptionDTO;
import com.devflow.devflow.dto.AdminMenuEntryDTO;

@WebMvcTest(
    controllers = {
        AuditLogController.class,
        AutomationController.class,
        CommentController.class,
        IssueController.class,
        ProjectController.class,
        ProjectReportController.class,
        SprintController.class,
        TestingController.class,
        UserController.class,
        AdminConfigurationController.class
    },
    properties = { "devflow.jwt.secret=devflow-test-signing-key-12345678901234567890" }
)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    CorsConfig.class,
    IssueService.class,
    RequestPermissionService.class,
    RequestPermissionAuthorizationManager.class
})
class RbacSecurityTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String MANAGER_EMAIL = "manager@example.com";
    private static final String DEVELOPER_EMAIL = "developer@example.com";
    private static final String OTHER_DEVELOPER_EMAIL = "other@example.com";
    private static final String VIEWER_EMAIL = "viewer@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private UserService userService;
    @MockitoBean private UserMembershipService userMembershipService;
    @MockitoBean private AdminConfigurationService adminConfigurationService;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private ProjectAccessService projectAccessService;
    @MockitoBean private EffectivePermissionService effectivePermissionService;
    private final Map<String, Set<String>> permissionKeysByEmail = new ConcurrentHashMap<>();
    private final Set<String> recoveryCapabilityEmails = ConcurrentHashMap.newKeySet();
    @MockitoBean private ProjectPrivacyInterceptor projectPrivacyInterceptor;
    @MockitoBean private SprintService sprintService;
    @MockitoBean private CommentService commentService;
    @MockitoBean private IssueRepository issueRepository;
    @MockitoBean private IssueDependencyRepository issueDependencyRepository;
    @MockitoBean private ProjectRepository projectRepository;
    @MockitoBean private SprintRepository sprintRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private CommentRepository commentRepository;
    @MockitoBean private AuditLogsRepository auditLogsRepository;
    @MockitoBean private TestExecutionRepository testExecutionRepository;
    @MockitoBean private AuditLogServices auditLogService;
    @MockitoBean private AutomationRuleService automationRuleService;
    @MockitoBean private AutomationEventService automationEventService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private TaskActivityTimelineService taskActivityTimelineService;
    @MockitoBean private TestExecutionService testExecutionService;
    @MockitoBean private IssueApprovalService issueApprovalService;
    @MockitoBean private IssueMaintenanceService issueMaintenanceService;
    @MockitoBean private ProjectAssignableMemberService projectAssignableMemberService;
    @MockitoBean private ProjectReportService projectReportService;
    @MockitoBean private SprintBurndownSnapshotService sprintBurndownSnapshotService;
    @MockitoBean private CurrentUserAuthenticationService currentUserAuthenticationService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(effectivePermissionService.has(any(), any(),
                org.mockito.ArgumentMatchers.nullable(Long.class), org.mockito.ArgumentMatchers.nullable(Long.class)))
            .thenAnswer(call -> hasFixturePermission(call.getArgument(0), call.getArgument(1)));
        org.mockito.Mockito.lenient().when(effectivePermissionService.hasAnywhere(any(), any()))
            .thenAnswer(call -> hasFixturePermission(call.getArgument(0), call.getArgument(1)));
        org.mockito.Mockito.lenient().when(effectivePermissionService.hasGlobal(any(), any()))
            .thenAnswer(call -> hasFixturePermission(call.getArgument(0), call.getArgument(1)));
        when(projectPrivacyInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(projectAccessService.visibleProjectIds(any())).thenReturn(List.of(5L));
        when(userService.getAllUsers()).thenReturn(List.of());
        when(projectService.getProjectById(anyLong())).thenReturn(Optional.of(new Project()));
        when(projectService.getProjectById(anyLong(), any())).thenReturn(Optional.of(project(5L)));
        when(sprintService.getSprintById(anyLong())).thenReturn(Optional.of(new Sprint()));
        when(issueRepository.findById(anyLong())).thenReturn(Optional.of(new Issue()));
        when(issueRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(new Issue()));
        when(issueRepository.findAll()).thenReturn(List.of());
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentService.getCommentsByIssue(anyLong())).thenReturn(List.of());
        Project existingProject = project(5L);
        Sprint existingSprint = sprint(9L);
        existingSprint.setProject(existingProject);
        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(sprintRepository.findById(9L)).thenReturn(Optional.of(existingSprint));
    }

    @Test
    void onlyAdminCanAccessUserManagement() throws Exception {
        User listedUser = user(55L, "viewer@example.com", "VIEWER");
        listedUser.setPassword("$2a$hashed-password");
        when(userService.getAllUsers()).thenReturn(List.of(listedUser));

        mockMvc.perform(get("/api/users").header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(55))
            .andExpect(jsonPath("$[0].name").value("viewer@example.com"))
            .andExpect(jsonPath("$[0].email").value("viewer@example.com"))
            .andExpect(jsonPath("$[0].role").value("VIEWER"))
            .andExpect(jsonPath("$[0].password").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hashed-password"))));

        for (String role : List.of("PROJECT_MANAGER", "DEVELOPER", "VIEWER")) {
            mockMvc.perform(get("/api/users").header("Authorization", bearer("user@example.com", role)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void membershipDetailsRequireUserManagementPermission() throws Exception {
        when(userMembershipService.getMemberships(55L, ADMIN_EMAIL))
            .thenReturn(new UserMembershipsResponseDTO(List.of(), List.of()));

        mockMvc.perform(get("/api/users/55/memberships")
                .header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projects").isArray())
            .andExpect(jsonPath("$.teams").isArray());
        mockMvc.perform(get("/api/users/55/memberships")
                .header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/55/memberships"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void automationManagementIsLimitedToAdminsAndProjectManagers() throws Exception {
        when(automationRuleService.listRules(ADMIN_EMAIL)).thenReturn(List.of());
        when(automationRuleService.listRules(MANAGER_EMAIL)).thenReturn(List.of());

        mockMvc.perform(get("/api/automations/rules").header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/automations/rules").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/automations/rules").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/automations/rules").header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/automations/rules"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanChangeAUsersRoleAndReceivesSafeResponse() throws Exception {
        for (String role : List.of("DEVELOPER", "PROJECT_MANAGER", "ADMIN")) {
            User updated = user(55L, "target@example.com", role);
            updated.setPassword("$2a$hashed-password");
            when(userService.updateUserRole(eq(55L), eq(role), eq(ADMIN_EMAIL))).thenReturn(updated);

            mockMvc.perform(put("/api/users/55/role")
                    .header("Authorization", bearer(ADMIN_EMAIL, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hashed-password"))));
        }
    }

    @Test
    void nonAdminsCannotListUsersOrChangeRoles() throws Exception {
        for (String role : List.of("DEVELOPER", "PROJECT_MANAGER", "VIEWER")) {
            String authorization = bearer("user@example.com", role);
            mockMvc.perform(get("/api/users").header("Authorization", authorization))
                .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/users/55/role").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminPanelConfigurationRoutesRequireProtectedRecoveryCapability() throws Exception {
        when(adminConfigurationService.adminMenu(ADMIN_EMAIL)).thenReturn(List.of(
            new AdminMenuEntryDTO("dashboard", "Dashboard", true, 10, null)));
        when(adminConfigurationService.statuses()).thenReturn(List.of("OPEN", "IN_PROGRESS"));
        when(adminConfigurationService.createOption(eq("PRIORITY"), any(), eq(ADMIN_EMAIL)))
            .thenReturn(new AdminDropdownOptionDTO(1L, "PRIORITY", "URGENT", "Urgent", true, 50, 0));

        mockMvc.perform(get("/api/admin/config/menu")
                .header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].routeKey").value("dashboard"));
        mockMvc.perform(post("/api/admin/config/dropdowns/PRIORITY")
                .header("Authorization", bearer(ADMIN_EMAIL, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"URGENT\",\"displayLabel\":\"Urgent\",\"sortOrder\":50}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("URGENT"));

        for (String role : List.of("PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER")) {
            mockMvc.perform(get("/api/admin/config/menu")
                    .header("Authorization", bearer("user@example.com", role)))
                .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/config/dropdowns/PRIORITY")
                    .header("Authorization", bearer("user@example.com", role))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"URGENT\",\"displayLabel\":\"Urgent\"}"))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/admin/config/menu"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUsersCanReadProtectedWorkflowOptionsButCannotEditThem() throws Exception {
        when(adminConfigurationService.statuses()).thenReturn(List.of("OPEN", "IN_PROGRESS", "RESOLVED"));
        mockMvc.perform(get("/api/config/statuses")
                .header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("OPEN"));
        mockMvc.perform(get("/api/config/statuses"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/config/menu/dashboard")
                .header("Authorization", bearer(VIEWER_EMAIL, "VIEWER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayLabel\":\"Home\",\"enabled\":true,\"sortOrder\":10}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownApiRoutesFailClosedAndOnlyKnownAuthenticationRoutesArePublic() throws Exception {
        mockMvc.perform(get("/api/issues/1/not-a-supported-action")
                .header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/not-a-supported-action"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void everyAuthenticatedRoleCanReadAssignableUsers() throws Exception {
        User directoryUser = user(77L, "private@example.com", "DEVELOPER");
        directoryUser.setAvatarKey("avatar-key");
        when(userService.getAllUsers()).thenReturn(List.of(directoryUser));
        for (String role : List.of("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "VIEWER")) {
            mockMvc.perform(get("/api/users/assignable")
                    .header("Authorization", bearer("user@example.com", role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(77))
                .andExpect(jsonPath("$[0].name").value("private@example.com"))
                .andExpect(jsonPath("$[0].role").value("DEVELOPER"))
                .andExpect(jsonPath("$[0].avatarUrl").value("/api/users/77/avatar"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
        }

        mockMvc.perform(get("/api/users/assignable"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users").header("Authorization", bearer("user@example.com", "DEVELOPER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanDeleteAuditLogs() throws Exception {
        mockMvc.perform(delete("/api/audit-logs/1").header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/audit-logs/1").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminAndProjectManagerCanSearchAuditLogs() throws Exception {
        when(auditLogService.searchLogs(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/api/audit-logs").header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit-logs").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER")))
            .andExpect(status().isOk());

        for (String role : List.of("DEVELOPER", "TESTER", "VIEWER")) {
            mockMvc.perform(get("/api/audit-logs").header("Authorization", bearer("user@example.com", role)))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminAndProjectManagerCanManageIssueDependencies() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_MANAGER")) {
            String email = role.equals("ADMIN") ? ADMIN_EMAIL : MANAGER_EMAIL;
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(1L, email, role)));
            mockMvc.perform(post("/api/issues/10/dependencies")
                    .header("Authorization", bearer(email, role))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"blockingIssueId\":20,\"reason\":\"Waiting on API\"}"))
                .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/issues/10/dependencies")
                .header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockingIssueId\":20,\"reason\":\"Waiting on API\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/issues/10/dependencies/20")
                .header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteIssueWithoutCsrfAndUnauthorizedRolesRemainBlocked() throws Exception {
        User admin = user(1L, ADMIN_EMAIL, "ADMIN");
        Issue issue = issueAssignedTo(10L);
        issue.setTitle("Issue to delete");
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(issueRepository.findById(2L)).thenReturn(Optional.of(issue));

        mockMvc.perform(delete("/api/issues/2").header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isNoContent());

        verify(issueRepository).deleteById(2L);
        verify(auditLogService).createLog(eq("ISSUE_DELETED"), any(), eq(null), eq(1L));

        mockMvc.perform(delete("/api/issues/2").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/issues/2").header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void projectManagerCanDeleteIssuesAndProjects() throws Exception {
        User manager = user(7L, MANAGER_EMAIL, "PROJECT_MANAGER");
        Issue issue = issueAssignedTo(10L);
        issue.setTitle("Issue to delete");
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager));
        when(issueRepository.findById(3L)).thenReturn(Optional.of(issue));

        mockMvc.perform(delete("/api/issues/3").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER")))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/projects/3").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER")))
            .andExpect(status().isOk());

        verify(issueRepository).deleteById(3L);
        verify(projectService).deleteProject(3L, MANAGER_EMAIL);
    }

    @Test
    void deletingMissingIssueReturnsNotFound() throws Exception {
        User admin = user(1L, ADMIN_EMAIL, "ADMIN");
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(issueRepository.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/issues/404").header("Authorization", bearer(ADMIN_EMAIL, "ADMIN")))
            .andExpect(status().isNotFound());

        org.mockito.Mockito.verify(issueRepository, org.mockito.Mockito.never()).deleteById(404L);
    }

    @Test
    void adminAndProjectManagerCanUpdateIssues() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_MANAGER")) {
            String email = role.equals("ADMIN") ? ADMIN_EMAIL : MANAGER_EMAIL;
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(7L, email, role)));

            mockMvc.perform(put("/api/issues/1")
                    .header("Authorization", bearer(email, role))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Updated by " + role + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated by " + role));
        }
    }

    @Test
    void issueCreateAndReadEndpointsUseTheSameSafeResponseShape() throws Exception {
        User manager = user(7L, MANAGER_EMAIL, "PROJECT_MANAGER");
        manager.setPassword("sensitive-hash");
        Issue issue = issueAssignedTo(10L);
        issue.setReportedBy(manager);
        issue.setTitle("Safe response issue");
        issue.setStatus("OPEN");
        issue.getAssignedUser().setPassword("developer-hash");
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager));
        when(issueRepository.findAll()).thenReturn(List.of(issue));
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project(5L)));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> {
            Issue created = invocation.getArgument(0);
            created.setId(44L);
            created.setTitle("Created through API");
            created.setStatus("OPEN");
            created.setReportedBy(manager);
            return created;
        });

        String authorization = bearer(MANAGER_EMAIL, "PROJECT_MANAGER");
        mockMvc.perform(get("/api/issues").header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].assignedUser.id").value(10))
            .andExpect(jsonPath("$[0].assignedUser.password").doesNotExist())
            .andExpect(jsonPath("$[0].assignedUser.email").doesNotExist())
            .andExpect(jsonPath("$[0].reportedBy.password").doesNotExist());

        mockMvc.perform(get("/api/issues/1").header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.project.id").value(5))
            .andExpect(jsonPath("$.assignedUser.password").doesNotExist())
            .andExpect(jsonPath("$.assignedUser.email").doesNotExist());

        mockMvc.perform(post("/api/issues").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Created through API\",\"project\":{\"id\":5}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(44))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.reportedBy.id").value(7))
            .andExpect(jsonPath("$.reportedBy.password").doesNotExist())
            .andExpect(jsonPath("$.reportedBy.email").doesNotExist());
    }

    @Test
    void adminAndProjectManagerCanUseOnlyApprovedManualStatusTransitions() throws Exception {
        List<String> manualStatuses = List.of("OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING");
        for (String role : List.of("ADMIN", "PROJECT_MANAGER")) {
            String email = role.equals("ADMIN") ? ADMIN_EMAIL : MANAGER_EMAIL;
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(7L, email, role)));

            for (String currentStatus : manualStatuses) {
                for (String requestedStatus : manualStatuses) {
                    Issue issue = issueAssignedTo(10L);
                    issue.setStatus(currentStatus);
                    when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));

                    var result = mockMvc.perform(put("/api/issues/1")
                            .header("Authorization", bearer(email, role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + role + " edit\",\"status\":\"" + requestedStatus + "\"}"));
                    if (issueAllowsManagerTransition(currentStatus, requestedStatus)) {
                        result.andExpect(status().isOk())
                            .andExpect(jsonPath("$.title").value(role + " edit"))
                            .andExpect(jsonPath("$.status").value(requestedStatus));
                    } else {
                        result.andExpect(status().isConflict());
                    }
                }
            }
        }
    }

    private boolean issueAllowsManagerTransition(String currentStatus, String requestedStatus) {
        if (currentStatus.equals(requestedStatus)) return true;
        return switch (currentStatus) {
            case "OPEN" -> "IN_PROGRESS".equals(requestedStatus);
            case "IN_PROGRESS" -> "CODE_REVIEW".equals(requestedStatus);
            case "CODE_REVIEW" -> "READY_FOR_TESTING".equals(requestedStatus)
                || "IN_PROGRESS".equals(requestedStatus);
            case "READY_FOR_TESTING" -> "IN_PROGRESS".equals(requestedStatus);
            default -> false;
        };
    }

    @Test
    void issueUpdateKeepsTestingWorkflowProtectedAndRejectsUnknownStatuses() throws Exception {
        String email = MANAGER_EMAIL;
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(7L, email, "PROJECT_MANAGER")));

        for (String currentStatus : List.of("READY_FOR_TESTING", "TESTING")) {
            Issue issue = issueAssignedTo(10L);
            issue.setStatus(currentStatus);
            when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));
            mockMvc.perform(put("/api/issues/1")
                    .header("Authorization", bearer(email, "PROJECT_MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
        }

        Issue readyIssue = issueAssignedTo(10L);
        readyIssue.setStatus("READY_FOR_TESTING");
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(readyIssue));
        mockMvc.perform(put("/api/issues/1")
                .header("Authorization", bearer(email, "PROJECT_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NOT_A_STATUS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void projectManagerCanManageProjectsAndSprints() throws Exception {
        String authorization = bearer(MANAGER_EMAIL, "PROJECT_MANAGER");

        mockMvc.perform(post("/api/projects").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/projects/1").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/projects/1").header("Authorization", authorization))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/sprints").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/sprints/1").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/sprints/1").header("Authorization", authorization))
            .andExpect(status().isOk());
    }

    @Test
    void developerCannotManageProjectsOrSprints() throws Exception {
        String authorization = bearer(DEVELOPER_EMAIL, "DEVELOPER");

        mockMvc.perform(post("/api/projects").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/1").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/1").header("Authorization", authorization))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sprints").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/sprints/1").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/sprints/1").header("Authorization", authorization))
            .andExpect(status().isForbidden());
    }

    @Test
    void developerCanCreateIssueButCannotChooseAssignee() throws Exception {
        User developer = user(10L, DEVELOPER_EMAIL, "DEVELOPER");
        when(userRepository.findByEmail(DEVELOPER_EMAIL)).thenReturn(Optional.of(developer));

        mockMvc.perform(post("/api/issues").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New issue\",\"assignedUser\":{\"id\":20}}"))
            .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<Issue> savedIssue = org.mockito.ArgumentCaptor.forClass(Issue.class);
        org.mockito.Mockito.verify(issueRepository).save(savedIssue.capture());
        org.junit.jupiter.api.Assertions.assertNull(savedIssue.getValue().getAssignedUser());
    }

    @Test
    void developerCannotModifyIssueAssignedToAnotherDeveloper() throws Exception {
        User developer = user(10L, DEVELOPER_EMAIL, "DEVELOPER");
        Issue issue = issueAssignedTo(20L);
        when(userRepository.findByEmail(DEVELOPER_EMAIL)).thenReturn(Optional.of(developer));
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));

        mockMvc.perform(put("/api/issues/1").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER"))
                .contentType(MediaType.APPLICATION_JSON).content(issueJson(20L, 5L, 9L)))
            .andExpect(status().isForbidden());
    }

    @Test
    void developerCanModifyTheirAssignedIssue() throws Exception {
        User developer = user(10L, DEVELOPER_EMAIL, "DEVELOPER");
        Issue issue = issueAssignedTo(10L);
        when(userRepository.findByEmail(DEVELOPER_EMAIL)).thenReturn(Optional.of(developer));
        when(userRepository.findById(10L)).thenReturn(Optional.of(developer));
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));

        mockMvc.perform(put("/api/issues/1").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER"))
                .contentType(MediaType.APPLICATION_JSON).content(issueJson(10L, 5L, 9L)))
            .andExpect(status().isOk());
    }

    @Test
    void partialIssueUpdatePreservesOmittedRelationshipsAndReturnsSafeResponse() throws Exception {
        User tester = user(30L, "tester@example.com", "TESTER");
        Issue issue = issueAssignedTo(10L);
        issue.setTester(tester);
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(user(7L, MANAGER_EMAIL, "PROJECT_MANAGER")));
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));

        mockMvc.perform(put("/api/issues/1").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed title\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Changed title"))
            .andExpect(jsonPath("$.project.id").value(5))
            .andExpect(jsonPath("$.assignedUser.id").value(10))
            .andExpect(jsonPath("$.sprint.id").value(9))
            .andExpect(jsonPath("$.tester.id").value(30))
            .andExpect(jsonPath("$.tester.password").doesNotExist())
            .andExpect(jsonPath("$.tester.email").doesNotExist());
    }

    @Test
    void issueUpdateCanAssignSuppliedTesterWithoutClearingOtherOmittedFields() throws Exception {
        User tester = user(30L, "tester@example.com", "TESTER");
        Issue issue = issueAssignedTo(10L);
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(user(7L, MANAGER_EMAIL, "PROJECT_MANAGER")));
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(30L)).thenReturn(Optional.of(tester));

        mockMvc.perform(put("/api/issues/1").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed title\",\"tester\":{\"id\":30}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tester.id").value(30))
            .andExpect(jsonPath("$.assignedUser.id").value(10))
            .andExpect(jsonPath("$.project.id").value(5))
            .andExpect(jsonPath("$.sprint.id").value(9));
    }

    @Test
    void adminAndProjectManagerCanAssignTesterUsingEitherSupportedBodyShape() throws Exception {
        User tester = user(30L, "tester@example.com", "TESTER");
        Issue issue = issueAssignedTo(10L);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(30L)).thenReturn(Optional.of(tester));
        for (String role : List.of("ADMIN", "PROJECT_MANAGER")) {
            String email = role.equals("ADMIN") ? ADMIN_EMAIL : MANAGER_EMAIL;
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(7L, email, role)));
            String body = role.equals("ADMIN") ? "{\"testerId\":30}" : "{\"tester\":{\"id\":30}}";
            mockMvc.perform(put("/api/issues/1/tester").header("Authorization", bearer(email, role))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tester.id").value(30))
                .andExpect(jsonPath("$.tester.password").doesNotExist());
        }
    }

    @Test
    void testerAssignmentRejectsMissingAndNonTesterIdsCleanly() throws Exception {
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(user(7L, MANAGER_EMAIL, "PROJECT_MANAGER")));
        mockMvc.perform(put("/api/issues/1/tester").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        Issue issue = issueAssignedTo(10L);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(31L)).thenReturn(Optional.of(user(31L, "developer@example.com", "DEVELOPER")));
        mockMvc.perform(put("/api/issues/1/tester").header("Authorization", bearer(MANAGER_EMAIL, "PROJECT_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"testerId\":31}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void developerCannotChangeAssigneeOnTheirIssue() throws Exception {
        assertRelationshipChangeForbidden(20L, 5L, 9L);
    }

    @Test
    void developerCannotChangeProjectOnTheirIssue() throws Exception {
        assertRelationshipChangeForbidden(10L, 6L, 9L);
    }

    @Test
    void developerCannotChangeSprintOnTheirIssue() throws Exception {
        assertRelationshipChangeForbidden(10L, 5L, 10L);
    }

    @Test
    void viewerCannotCreateOrModifyIssuesProjectsSprintsOrComments() throws Exception {
        String authorization = bearer(VIEWER_EMAIL, "VIEWER");
        String body = "{}";

        mockMvc.perform(post("/api/issues").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/issues/1").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/issues/1").header("Authorization", authorization))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/1").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/1").header("Authorization", authorization))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sprints").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/sprints/1").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/sprints/1").header("Authorization", authorization))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/comments").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/comments/1").header("Authorization", authorization))
            .andExpect(status().isForbidden());
    }

    @Test
    void everyRoleCanReadProjectsIssuesSprintsAndComments() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER")) {
            String authorization = bearer(role.toLowerCase() + "@example.com", role);
            mockMvc.perform(get("/api/projects").header("Authorization", authorization)).andExpect(status().isOk());
            mockMvc.perform(get("/api/projects/1").header("Authorization", authorization)).andExpect(status().isOk());
            mockMvc.perform(get("/api/issues").header("Authorization", authorization)).andExpect(status().isOk());
            mockMvc.perform(get("/api/issues/1").header("Authorization", authorization)).andExpect(status().isOk());
            mockMvc.perform(get("/api/sprints").header("Authorization", authorization)).andExpect(status().isOk());
            mockMvc.perform(get("/api/sprints/1").header("Authorization", authorization)).andExpect(status().isOk());
            mockMvc.perform(get("/api/comments/issue/1").header("Authorization", authorization)).andExpect(status().isOk());
        }
    }

    @Test
    void testingAndReportEndpointsEnforceRoleRules() throws Exception {
        when(testExecutionService.getQueue(any())).thenReturn(List.of());
        when(testExecutionService.getMyQueue(any())).thenReturn(List.of());
        when(testExecutionService.getHistory(eq(1L), any())).thenReturn(List.of());
        when(projectReportService.getProjectReport(eq(1L), any(), any(), any(), any(), any()))
            .thenReturn(new com.devflow.devflow.dto.ProjectReportDTO(1L, "Project", "", List.of(),
                new com.devflow.devflow.dto.ProjectReportSummaryDTO(0, 0, 0, 0, 0, 0)));

        mockMvc.perform(get("/api/testing/queue").header("Authorization", bearer("tester@example.com", "TESTER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/testing/queue").header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/testing/my").header("Authorization", bearer("tester@example.com", "TESTER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/testing/my").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/testing/issue/1/history").header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/projects/1").header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/projects/1"))
            .andExpect(status().isUnauthorized());

        for (String role : List.of("DEVELOPER", "VIEWER", "TESTER")) {
            mockMvc.perform(put("/api/issues/1/tester")
                    .header("Authorization", bearer("user@example.com", role))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"testerId\":5}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void authenticatedRolesCanReadSprintBurndown() throws Exception {
        when(sprintBurndownSnapshotService.getBurndown(9L)).thenReturn(
            new com.devflow.devflow.dto.SprintBurndownResponseDTO(9L, "Sprint 9", null, null, null, null, List.of()));

        mockMvc.perform(get("/api/reports/sprints/9/burndown")
                .header("Authorization", bearer(VIEWER_EMAIL, "VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sprintId").value(9))
            .andExpect(jsonPath("$.snapshots").isArray())
            .andExpect(jsonPath("$.snapshots").isEmpty());
    }

    @Test
    void anonymousUsersCannotReadSprintBurndown() throws Exception {
        mockMvc.perform(get("/api/reports/sprints/9/burndown"))
            .andExpect(status().isUnauthorized());
    }

    private void assertRelationshipChangeForbidden(Long requestedAssignee, Long requestedProject, Long requestedSprint)
            throws Exception {
        User developer = user(10L, DEVELOPER_EMAIL, "DEVELOPER");
        when(userRepository.findByEmail(DEVELOPER_EMAIL)).thenReturn(Optional.of(developer));
        Issue issue = issueAssignedTo(10L);
        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));

        mockMvc.perform(put("/api/issues/1").header("Authorization", bearer(DEVELOPER_EMAIL, "DEVELOPER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueJson(requestedAssignee, requestedProject, requestedSprint)))
            .andExpect(status().isForbidden());
    }

    private Issue issueAssignedTo(Long assigneeId) {
        Issue issue = new Issue();
        issue.setId(1L);
        issue.setTitle("Existing issue");
        Project project = project(5L);
        Sprint sprint = sprint(9L);
        sprint.setProject(project);
        issue.setProject(project);
        issue.setSprint(sprint);
        issue.setAssignedUser(user(assigneeId, "assignee@example.com", "DEVELOPER"));
        return issue;
    }

    private String issueJson(Long assigneeId, Long projectId, Long sprintId) {
        return "{\"title\":\"Updated issue\",\"status\":\"IN_PROGRESS\","
            + "\"assignedUser\":{\"id\":" + assigneeId + "},"
            + "\"project\":{\"id\":" + projectId + "},"
            + "\"sprint\":{\"id\":" + sprintId + "}}";
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setAdminRecovery("ADMIN".equals(role));
        if (user.hasAdminRecovery()) recoveryCapabilityEmails.add(email);
        permissionKeysByEmail.put(email, defaultPermissions(role));
        return user;
    }

    private Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    private Sprint sprint(Long id) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        return sprint;
    }

    private String bearer(String email, String role) {
        if ("ADMIN".equals(role)) recoveryCapabilityEmails.add(email);
        permissionKeysByEmail.put(email, defaultPermissions(role));
        String token = jwtService.generateToken(email, role);
        org.mockito.Mockito.lenient().when(currentUserAuthenticationService.authenticateToken(token))
            .thenReturn(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                email, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        return "Bearer " + token;
    }

    private boolean hasFixturePermission(String email, String permission) {
        if ("ROLE_MANAGE".equals(permission)) return recoveryCapabilityEmails.contains(email);
        return permissionKeysByEmail.getOrDefault(email, Set.of()).contains(permission);
    }

    private Set<String> defaultPermissions(String role) {
        if ("ADMIN".equals(role)) return PermissionCatalogue.entries().stream()
            .map(PermissionCatalogue.Entry::key).filter(key -> !"ROLE_MANAGE".equals(key))
            .collect(java.util.stream.Collectors.toSet());
        Set<String> read = new HashSet<>(List.of("PROJECT_READ", "ISSUE_READ", "COMMENT_READ", "REPORT_READ",
            "DASHBOARD_READ", "TESTING_HISTORY_READ", "TEAM_READ", "TEAM_ACTIVITY_READ"));
        switch (role) {
            case "PROJECT_MANAGER" -> read.addAll(List.of("PROJECT_CREATE", "TEAM_CREATE", "PROJECT_UPDATE",
                "PROJECT_DELETE", "PROJECT_ROLE_MANAGE", "ISSUE_CREATE", "ISSUE_UPDATE", "ISSUE_UPDATE_ALL",
                "ISSUE_ASSIGN_DEVELOPER", "ISSUE_STATUS_MANAGE", "ISSUE_DELETE", "TESTER_ASSIGN",
                "ISSUE_DEPENDENCY_MANAGE", "GITHUB_ISSUE_CREATE", "COMMENT_CREATE", "COMMENT_DELETE",
                "AUDIT_READ", "AUTOMATION_MANAGE", "TESTING_QUEUE_READ", "TESTING_HISTORY_READ"));
            case "DEVELOPER" -> read.addAll(List.of("ISSUE_CREATE", "ISSUE_UPDATE", "ISSUE_UPDATE_ASSIGNED",
                "ISSUE_STATUS_ADVANCE", "COMMENT_CREATE", "ASSIGNABLE_DEVELOPER"));
            case "TESTER" -> read.addAll(List.of("TESTING_MY_READ", "TESTING_QUEUE_READ", "TEST_EXECUTE", "ASSIGNABLE_TESTER"));
            default -> { }
        }
        return Set.copyOf(read);
    }
}
