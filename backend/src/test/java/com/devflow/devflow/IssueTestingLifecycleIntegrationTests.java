package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.service.AuditLogServices;

@SpringBootTest
@AutoConfigureMockMvc
class IssueTestingLifecycleIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private TestExecutionRepository executionRepository;
    @Autowired private EntityManager entityManager;
    @MockitoBean private AuditLogServices auditLogService;

    @Test
    void manualIssueUpdateCannotStartOrResolveTesting() throws Exception {
        User manager = user("PROJECT_MANAGER");
        Issue issue = issue(manager, null, "READY_FOR_TESTING");

        mockMvc.perform(put("/api/issues/{id}", issue.getId()).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TESTING\"}"))
            .andExpect(status().isConflict());
        mockMvc.perform(put("/api/issues/{id}", issue.getId()).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isConflict());

        assertEquals("READY_FOR_TESTING", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
    }

    @Test
    void issueCreationCannotBeginInTestingOrResolvedState() throws Exception {
        User admin = user("ADMIN");
        for (String status : new String[] {"TESTING", "RESOLVED"}) {
            mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Invalid initial state\",\"status\":\"" + status + "\"}"))
                .andExpect(status().isConflict());
        }
    }

    @Test
    void creationDefaultsToOpenAndAcceptsOnlyManualWorkflowStates() throws Exception {
        User admin = user("ADMIN");
        mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Default status\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"));

        for (String issueStatus : new String[] {"OPEN", "IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING"}) {
            mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Allowed " + issueStatus + "\",\"status\":\"" + issueStatus + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(issueStatus));
        }
    }

    @Test
    void unknownMalformedAndLegacyStatusesAreRejectedOnCreateAndUpdate() throws Exception {
        User admin = user("ADMIN");
        Issue issue = issue(admin, null, "OPEN");
        for (String invalidStatus : new String[] {"SUPER_DONE", "in progress", "TODO", "CLOSED", ""}) {
            String body = "{\"title\":\"Invalid status\",\"status\":\"" + invalidStatus + "\"}";
            mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/issues/{id}", issue.getId()).header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + invalidStatus + "\"}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals("OPEN", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
    }

    @Test
    void onlyExactlyAssignedTesterCanStartAndDuplicateStartsAreRejected() throws Exception {
        User assignedTester = user("TESTER");
        User otherTester = user("TESTER");
        User developer = user("DEVELOPER");
        Issue issue = issue(developer, assignedTester, "READY_FOR_TESTING");

        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(otherTester)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(assignedTester)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(assignedTester)))
            .andExpect(status().isConflict());

        assertEquals("TESTING", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
        assertEquals(1, executionRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId()).size());
    }

    @Test
    void passResolvesIssueAndPersistsCompletedExecutionHistory() throws Exception {
        User tester = user("TESTER");
        User developer = user("DEVELOPER");
        Issue issue = issue(developer, tester, "READY_FOR_TESTING");

        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(tester)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/testing/{id}/complete", issue.getId())
                .header("Authorization", bearer(tester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"PASSED\",\"testScenario\":\"Sign in with valid credentials\","
                    + "\"expectedResult\":\"Dashboard opens\",\"actualResult\":\"Dashboard opened\","
                    + "\"notes\":\"All acceptance checks passed.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("PASSED"))
            .andExpect(jsonPath("$.testScenario").value("Sign in with valid credentials"))
            .andExpect(jsonPath("$.expectedResult").value("Dashboard opens"))
            .andExpect(jsonPath("$.actualResult").value("Dashboard opened"));

        assertEquals("TEAM_LEAD_APPROVAL", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
        var history = executionRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId());
        assertEquals(1, history.size());
        assertEquals("COMPLETED", history.get(0).getStatus());
        assertEquals("PASSED", history.get(0).getResult());
        assertEquals("Sign in with valid credentials", history.get(0).getTestScenario());
        assertEquals("Dashboard opens", history.get(0).getExpectedResult());
        assertEquals("Dashboard opened", history.get(0).getActualResult());
    }

    @Test
    void adminCanStartAndSubmitATestResult() throws Exception {
        User admin = user("ADMIN");
        User assignedTester = user("TESTER");
        User developer = user("DEVELOPER");
        Issue issue = issue(developer, assignedTester, "READY_FOR_TESTING");

        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(admin)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/testing/{id}/complete", issue.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"PASSED\",\"testScenario\":\"Admin verification\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tester.id").value(admin.getId()))
            .andExpect(jsonPath("$.testScenario").value("Admin verification"));

        assertEquals("TEAM_LEAD_APPROVAL", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
        assertEquals(admin.getId(), executionRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId())
            .get(0).getTester().getId());
    }

    @Test
    void acceptanceCriteriaCanBeCreatedUpdatedAndRead() throws Exception {
        User admin = user("ADMIN");
        String created = mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Criteria issue\",\"acceptanceCriteria\":\"Valid users can sign in.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.acceptanceCriteria").value("Valid users can sign in."))
            .andReturn().getResponse().getContentAsString();
        Long issueId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/issues/{id}", issueId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acceptanceCriteria\":\"Invalid credentials show an error.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptanceCriteria").value("Invalid credentials show an error."));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/issues/{id}", issueId).header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptanceCriteria").value("Invalid credentials show an error."));
    }

    @Test
    void dueDateCanBeCreatedReadUpdatedAndExplicitlyClearedWithoutChangingOnOmission() throws Exception {
        User admin = user("ADMIN");
        String created = mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Deadline task\",\"dueDate\":\"2026-11-15\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
            .andReturn().getResponse().getContentAsString();
        Long issueId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/issues/{id}", issueId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Change details only\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Change details only"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-15"));

        mockMvc.perform(put("/api/issues/{id}", issueId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2026-11-20\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueDate").value("2026-11-20"));

        mockMvc.perform(put("/api/issues/{id}", issueId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void malformedDueDateIsRejected() throws Exception {
        User admin = user("ADMIN");
        mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Invalid date task\",\"dueDate\":\"not-a-date\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void failedTestRequiresNotesAndReturnsIssueToInProgress() throws Exception {
        User tester = user("TESTER");
        User developer = user("DEVELOPER");
        Issue issue = issue(developer, tester, "READY_FOR_TESTING");
        mockMvc.perform(post("/api/testing/{id}/start", issue.getId())
                .header("Authorization", bearer(tester)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/testing/{id}/complete", issue.getId())
                .header("Authorization", bearer(tester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\",\"notes\":\"  \"}"))
            .andExpect(status().isBadRequest());
        assertEquals("TESTING", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
        assertEquals("IN_PROGRESS", executionRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId())
            .get(0).getStatus());

        mockMvc.perform(post("/api/testing/{id}/complete", issue.getId())
                .header("Authorization", bearer(tester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\",\"notes\":\"Login rejects valid special characters.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("FAILED"));
        assertEquals("IN_PROGRESS", issueRepository.findById(issue.getId()).orElseThrow().getStatus());
        TestExecution failed = executionRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId()).get(0);
        assertEquals("COMPLETED", failed.getStatus());
        assertEquals("Login rejects valid special characters.", failed.getNotes());
    }

    @Test
    void auditFailureRollsBackStartAndCompletionDatabaseChanges() throws Exception {
        User tester = user("TESTER");
        User developer = user("DEVELOPER");
        Issue startIssue = issue(developer, tester, "READY_FOR_TESTING");
        doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Injected audit failure"))
            .when(auditLogService).createLog(eq("TEST_STARTED"), anyString(), any(), any());

        mockMvc.perform(post("/api/testing/{id}/start", startIssue.getId())
                .header("Authorization", bearer(tester)))
            .andExpect(status().isInternalServerError());
        entityManager.clear();
        assertEquals("READY_FOR_TESTING", issueRepository.findById(startIssue.getId()).orElseThrow().getStatus());
        assertEquals(0, executionRepository.findByIssueIdOrderByCreatedAtDesc(startIssue.getId()).size());

        // Reset the relevant mock stubbing and seed a valid active execution directly.
        org.mockito.Mockito.reset(auditLogService);
        Issue completeIssue = issue(developer, tester, "TESTING");
        TestExecution active = new TestExecution();
        active.setIssue(completeIssue);
        active.setTester(tester);
        active.setStatus("IN_PROGRESS");
        active.setStartedAt(java.time.LocalDateTime.now());
        executionRepository.saveAndFlush(active);
        doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Injected audit failure"))
            .when(auditLogService).createLog(eq("TEST_PASSED"), anyString(), any(), any());

        mockMvc.perform(post("/api/testing/{id}/complete", completeIssue.getId())
                .header("Authorization", bearer(tester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"PASSED\"}"))
            .andExpect(status().isInternalServerError());
        entityManager.clear();
        assertEquals("TESTING", issueRepository.findById(completeIssue.getId()).orElseThrow().getStatus());
        TestExecution stillActive = executionRepository
            .findByIssueIdOrderByCreatedAtDesc(completeIssue.getId()).get(0);
        assertEquals("IN_PROGRESS", stillActive.getStatus());
        assertNull(stillActive.getResult());
        assertNull(stillActive.getCompletedAt());
    }

    private User user(String role) {
        String email = UUID.randomUUID().toString().replace("-", "") + "@phase3a.test";
        User user = new User();
        user.setEmail(email);
        user.setName(role);
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Issue issue(User developer, User tester, String status) {
        Project project = new Project();
        project.setName("Lifecycle project " + UUID.randomUUID());
        project = projectRepository.saveAndFlush(project);
        Issue issue = new Issue();
        issue.setTitle("Lifecycle issue");
        issue.setStatus(status);
        issue.setProject(project);
        issue.setAssignedUser(developer);
        issue.setTester(tester);
        issue.setReportedBy(developer);
        return issueRepository.saveAndFlush(issue);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }
}
