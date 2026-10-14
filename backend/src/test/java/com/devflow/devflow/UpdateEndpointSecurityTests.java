package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.Comment;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class UpdateEndpointSecurityTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private AuditLogsRepository auditLogsRepository;
    @Autowired private TestExecutionRepository testExecutionRepository;

    private User admin;
    private User developer;
    private User tester;
    private User viewer;
    private Project project;
    private Project otherProject;
    private Sprint sprint;
    private Issue issue;
    private Comment comment;

    @BeforeEach
    void setUp() {
        admin = user("admin@update.test", "ADMIN");
        developer = user("developer@update.test", "DEVELOPER");
        tester = user("tester@update.test", "TESTER");
        viewer = user("viewer@update.test", "VIEWER");

        project = new Project();
        project.setName("Original project");
        project.setDescription("Keep this description");
        project.setStatus("ACTIVE");
        project.setStartDate(LocalDate.of(2025, 1, 1));
        project.setEndDate(LocalDate.of(2025, 12, 31));
        project = projectRepository.saveAndFlush(project);

        otherProject = new Project();
        otherProject.setName("Other project");
        otherProject = projectRepository.saveAndFlush(otherProject);

        sprint = new Sprint();
        sprint.setName("Original sprint");
        sprint.setGoal("Keep this goal");
        sprint.setStatus("ACTIVE");
        sprint.setStartDate(LocalDate.of(2025, 2, 1));
        sprint.setEndDate(LocalDate.of(2025, 2, 14));
        sprint.setProject(project);
        sprint = sprintRepository.saveAndFlush(sprint);

        issue = new Issue();
        issue.setTitle("Original issue");
        issue.setDescription("Keep this description");
        issue.setPriority("HIGH");
        issue.setStatus("OPEN");
        issue.setProject(project);
        issue.setSprint(sprint);
        issue.setAssignedUser(developer);
        issue.setTester(tester);
        issue.setReportedBy(admin);
        issue = issueRepository.saveAndFlush(issue);

        comment = new Comment();
        comment.setContent("Original comment");
        comment.setIssue(issue);
        comment.setUser(developer);
        comment.setCreatedAt(LocalDateTime.of(2025, 3, 1, 10, 0));
        comment = commentRepository.saveAndFlush(comment);
    }

    @Test
    void projectUpdateIgnoresProtectedFieldsAndPreservesOmittedFields() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", project.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999999,\"name\":\"Renamed\",\"createdAt\":\"2000-01-01\"}"))
            .andExpect(status().isOk());

        Project updated = projectRepository.findById(project.getId()).orElseThrow();
        assertEquals("Renamed", updated.getName());
        assertEquals("Keep this description", updated.getDescription());
        assertEquals("ACTIVE", updated.getStatus());
        assertEquals(LocalDate.of(2025, 1, 1), updated.getStartDate());
        assertEquals(LocalDate.of(2025, 12, 31), updated.getEndDate());
        assertFalse(projectRepository.existsById(999999L));
    }

    @Test
    void sprintUpdatePreservesOmittedFieldsAndProjectAndIgnoresClientId() throws Exception {
        mockMvc.perform(put("/api/sprints/{id}", sprint.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999999,\"goal\":\"Updated goal\"}"))
            .andExpect(status().isOk());

        Sprint updated = sprintRepository.findById(sprint.getId()).orElseThrow();
        assertEquals("Original sprint", updated.getName());
        assertEquals("Updated goal", updated.getGoal());
        assertEquals("ACTIVE", updated.getStatus());
        assertEquals(LocalDate.of(2025, 2, 1), updated.getStartDate());
        assertEquals(LocalDate.of(2025, 2, 14), updated.getEndDate());
        assertEquals(project.getId(), updated.getProject().getId());
        assertFalse(sprintRepository.existsById(999999L));
    }

    @Test
    void sprintUpdateResolvesProjectIdsAndRejectsInvalidProjectReference() throws Exception {
        mockMvc.perform(put("/api/sprints/{id}", sprint.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"project\":{\"id\":" + otherProject.getId() + ",\"name\":\"Injected\"}}"))
            .andExpect(status().isBadRequest());

        issue.setSprint(null);
        issueRepository.saveAndFlush(issue);
        mockMvc.perform(put("/api/sprints/{id}", sprint.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"project\":{\"id\":" + otherProject.getId() + ",\"name\":\"Injected\"}}"))
            .andExpect(status().isOk());
        assertEquals(otherProject.getId(), sprintRepository.findById(sprint.getId()).orElseThrow().getProject().getId());

        mockMvc.perform(put("/api/sprints/{id}", sprint.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"project\":{\"id\":999999}}"))
            .andExpect(status().isNotFound());
        assertEquals(otherProject.getId(), sprintRepository.findById(sprint.getId()).orElseThrow().getProject().getId());
    }

    @Test
    void userUpdateCannotChangeRolePasswordOrIdAndPreservesOmittedFields() throws Exception {
        String storedPassword = developer.getPassword();
        mockMvc.perform(put("/api/users/{id}", developer.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999999,\"name\":\"New name\",\"role\":\"ADMIN\",\"password\":\"plain\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("DEVELOPER"))
            .andExpect(jsonPath("$.password").doesNotExist());

        User updated = userRepository.findById(developer.getId()).orElseThrow();
        assertEquals("New name", updated.getName());
        assertEquals("developer@update.test", updated.getEmail());
        assertEquals("DEVELOPER", updated.getRole());
        assertEquals(storedPassword, updated.getPassword());
        assertFalse(userRepository.existsById(999999L));
    }

    @Test
    void userUpdateRejectsBlankEmailAndDuplicateEmailWithoutChangingAccount() throws Exception {
        mockMvc.perform(put("/api/users/{id}", developer.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/users/{id}", developer.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@update.test\"}"))
            .andExpect(status().isConflict());
        assertEquals("developer@update.test", userRepository.findById(developer.getId()).orElseThrow().getEmail());
    }

    @Test
    void issueUpdateIgnoresProtectedFieldsAndPreservesOmittedRelationships() throws Exception {
        String createdAt = issue.getCreatedAt().toString();
        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999999,\"title\":\"Updated issue\",\"reportedBy\":{\"id\":"
                    + viewer.getId() + "},\"createdAt\":\"2000-01-01T00:00:00\",\"tester\":{\"id\":"
                    + tester.getId() + ",\"role\":\"ADMIN\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tester.id").value(tester.getId()))
            .andExpect(jsonPath("$.reportedBy.id").value(admin.getId()))
            .andExpect(jsonPath("$.password").doesNotExist());

        Issue updated = issueRepository.findById(issue.getId()).orElseThrow();
        assertEquals("Updated issue", updated.getTitle());
        assertEquals("Keep this description", updated.getDescription());
        assertEquals("HIGH", updated.getPriority());
        assertEquals("OPEN", updated.getStatus());
        assertEquals(project.getId(), updated.getProject().getId());
        assertEquals(developer.getId(), updated.getAssignedUser().getId());
        assertEquals(tester.getId(), updated.getTester().getId());
        assertEquals(sprint.getId(), updated.getSprint().getId());
        assertEquals(admin.getId(), updated.getReportedBy().getId());
        assertEquals(createdAt, updated.getCreatedAt().toString());
        assertFalse(issueRepository.existsById(999999L));
    }

    @Test
    void issueUpdateValidatesDeveloperAndSprintReferences() throws Exception {
        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignedUser\":{\"id\":" + viewer.getId() + "}}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"project\":{\"id\":" + otherProject.getId() + "}}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"project\":{\"id\":999999}}"))
            .andExpect(status().isNotFound());

        Issue unchanged = issueRepository.findById(issue.getId()).orElseThrow();
        assertEquals(developer.getId(), unchanged.getAssignedUser().getId());
        assertEquals(project.getId(), unchanged.getProject().getId());
        assertEquals(sprint.getId(), unchanged.getSprint().getId());
    }

    @Test
    void explicitNullClearsNullableAssignmentsButNotCommentOwnership() throws Exception {
        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignedUser\":null,\"tester\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedUser").doesNotExist())
            .andExpect(jsonPath("$.tester").doesNotExist());

        Issue updated = issueRepository.findById(issue.getId()).orElseThrow();
        assertNull(updated.getAssignedUser());
        assertNull(updated.getTester());
        assertEquals(developer.getId(), commentRepository.findById(comment.getId()).orElseThrow().getUser().getId());
    }

    @Test
    void nonAdminCannotUpdateAdminManagedEntities() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", project.getId())
                .header("Authorization", bearer(developer)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/sprints/{id}", sprint.getId())
                .header("Authorization", bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}", developer.getId())
                .header("Authorization", bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void issueDeleteRetainsAuditRowsAndReturnsSuccessForAdminAndProjectManager() throws Exception {
        for (User actor : new User[] { admin, manager("manager@delete.test") }) {
            Issue deletable = new Issue();
            deletable.setTitle("Delete with audit history");
            deletable.setStatus("OPEN");
            deletable.setProject(project);
            deletable.setReportedBy(admin);
            deletable = issueRepository.saveAndFlush(deletable);

            AuditLog history = new AuditLog();
            history.setAction("STATUS_CHANGED");
            history.setDetails("An existing issue event");
            history.setCreatedAt(LocalDateTime.now());
            history.setIssue(deletable);
            history.setUser(admin);
            history = auditLogsRepository.saveAndFlush(history);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/issues/{id}", deletable.getId())
                    .header("Authorization", bearer(actor)))
                .andExpect(status().isNoContent());

            assertFalse(issueRepository.existsById(deletable.getId()));
            AuditLog retained = auditLogsRepository.findById(history.getId()).orElseThrow();
            assertNull(retained.getIssue());
        }
    }

    @Test
    void issueDeleteRefusesToDiscardCommentOrTestingHistory() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(admin)))
            .andExpect(status().isConflict());

        assertTrue(issueRepository.existsById(issue.getId()));
        assertTrue(commentRepository.existsById(comment.getId()));
        assertFalse(testExecutionRepository.existsByIssueId(issue.getId()));

        Issue issueWithTestHistory = new Issue();
        issueWithTestHistory.setTitle("Issue with prior testing");
        issueWithTestHistory.setStatus("RESOLVED");
        issueWithTestHistory.setProject(project);
        issueWithTestHistory.setReportedBy(admin);
        issueWithTestHistory = issueRepository.saveAndFlush(issueWithTestHistory);

        TestExecution execution = new TestExecution();
        execution.setIssue(issueWithTestHistory);
        execution.setTester(tester);
        execution.setStatus("COMPLETED");
        execution.setResult("PASSED");
        execution.setCreatedAt(LocalDateTime.now());
        testExecutionRepository.saveAndFlush(execution);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/issues/{id}", issueWithTestHistory.getId())
                .header("Authorization", bearer(admin)))
            .andExpect(status().isConflict());
        assertTrue(issueRepository.existsById(issueWithTestHistory.getId()));
        assertTrue(testExecutionRepository.existsByIssueId(issueWithTestHistory.getId()));
    }

    private User manager(String email) {
        User value = user(email, "PROJECT_MANAGER");
        return value;
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode("test-password"));
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }
}
