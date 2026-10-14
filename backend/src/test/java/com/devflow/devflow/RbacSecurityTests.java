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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.config.CorsConfig;
import com.devflow.devflow.controller.AuditLogController;
import com.devflow.devflow.controller.CommentController;
import com.devflow.devflow.controller.IssueController;
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
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.security.JwtAuthenticationFilter;
import com.devflow.devflow.security.CurrentUserAuthenticationService;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.security.SecurityConfig;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.CommentService;
import com.devflow.devflow.service.IssueService;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.ProjectService;
import com.devflow.devflow.service.ProjectReportService;
import com.devflow.devflow.service.SprintService;
import com.devflow.devflow.service.TestExecutionService;
import com.devflow.devflow.service.UserService;

@WebMvcTest(
    controllers = {
        AuditLogController.class,
        CommentController.class,
        IssueController.class,
        ProjectController.class,
        ProjectReportController.class,
        SprintController.class,
        TestingController.class,
        UserController.class
    },
    properties = "devflow.jwt.secret=devflow-test-signing-key-12345678901234567890"
)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    CorsConfig.class,
    IssueService.class
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
    @MockitoBean private ProjectService projectService;
    @MockitoBean private SprintService sprintService;
    @MockitoBean private CommentService commentService;
    @MockitoBean private IssueRepository issueRepository;
    @MockitoBean private ProjectRepository projectRepository;
    @MockitoBean private SprintRepository sprintRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private CommentRepository commentRepository;
    @MockitoBean private TestExecutionRepository testExecutionRepository;
    @MockitoBean private AuditLogServices auditLogService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private TestExecutionService testExecutionService;
    @MockitoBean private ProjectReportService projectReportService;
    @MockitoBean private CurrentUserAuthenticationService currentUserAuthenticationService;

    @BeforeEach
    void setUp() {
        when(userService.getAllUsers()).thenReturn(List.of());
        when(projectService.getProjectById(anyLong())).thenReturn(Optional.of(new Project()));
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
    void everyAuthenticatedRoleCanReadAssignableUsers() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "VIEWER")) {
            mockMvc.perform(get("/api/users/assignable")
                    .header("Authorization", bearer("user@example.com", role)))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/users/assignable"))
            .andExpect(status().isForbidden());

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
        verify(projectService).deleteProject(3L);
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
            .andExpect(jsonPath("$[0].reportedBy.password").doesNotExist());

        mockMvc.perform(get("/api/issues/1").header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.project.id").value(5))
            .andExpect(jsonPath("$.assignedUser.password").doesNotExist());

        mockMvc.perform(post("/api/issues").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Created through API\",\"project\":{\"id\":5}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(44))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.reportedBy.id").value(7))
            .andExpect(jsonPath("$.reportedBy.password").doesNotExist());
    }

    @Test
    void adminAndProjectManagerCanEditIssuesAcrossManualStatuses() throws Exception {
        List<String> manualStatuses = List.of("OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING");
        for (String role : List.of("ADMIN", "PROJECT_MANAGER")) {
            String email = role.equals("ADMIN") ? ADMIN_EMAIL : MANAGER_EMAIL;
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(7L, email, role)));

            for (String currentStatus : manualStatuses) {
                for (String requestedStatus : manualStatuses) {
                    Issue issue = issueAssignedTo(10L);
                    issue.setStatus(currentStatus);
                    when(issueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(issue));

                    mockMvc.perform(put("/api/issues/1")
                            .header("Authorization", bearer(email, role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + role + " edit\",\"status\":\"" + requestedStatus + "\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value(role + " edit"))
                        .andExpect(jsonPath("$.status").value(requestedStatus));
                }
            }
        }
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
            .andExpect(jsonPath("$.tester.password").doesNotExist());
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
        when(testExecutionService.getQueue()).thenReturn(List.of());
        when(testExecutionService.getMyQueue(any())).thenReturn(List.of());
        when(testExecutionService.getHistory(1L)).thenReturn(List.of());
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
            .andExpect(status().isForbidden());

        for (String role : List.of("DEVELOPER", "VIEWER", "TESTER")) {
            mockMvc.perform(put("/api/issues/1/tester")
                    .header("Authorization", bearer("user@example.com", role))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"testerId\":5}"))
                .andExpect(status().isForbidden());
        }
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
        String token = jwtService.generateToken(email, role);
        org.mockito.Mockito.lenient().when(currentUserAuthenticationService.authenticateToken(token))
            .thenReturn(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                email, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        return "Bearer " + token;
    }
}
