package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.Comment;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class CreateEndpointMassAssignmentTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private CommentRepository commentRepository;

    private User admin;
    private User developer;
    private User protectedUser;
    private Project protectedProject;
    private Sprint protectedSprint;
    private Issue protectedIssue;
    private Comment protectedComment;

    @BeforeEach
    void setUp() {
        admin = user("admin@mass-assignment.test", "ADMIN");
        developer = user("developer@mass-assignment.test", "DEVELOPER");
        protectedUser = user("protected@mass-assignment.test", "VIEWER");

        protectedProject = new Project();
        protectedProject.setName("Protected project");
        protectedProject = projectRepository.saveAndFlush(protectedProject);

        protectedSprint = new Sprint();
        protectedSprint.setName("Protected sprint");
        protectedSprint.setProject(protectedProject);
        protectedSprint = sprintRepository.saveAndFlush(protectedSprint);

        protectedIssue = new Issue();
        protectedIssue.setTitle("Protected issue");
        protectedIssue.setStatus("OPEN");
        protectedIssue.setProject(protectedProject);
        protectedIssue.setAssignedUser(developer);
        protectedIssue.setReportedBy(admin);
        protectedIssue = issueRepository.saveAndFlush(protectedIssue);

        protectedComment = new Comment();
        protectedComment.setContent("Protected comment");
        protectedComment.setIssue(protectedIssue);
        protectedComment.setUser(developer);
        protectedComment.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        protectedComment = commentRepository.saveAndFlush(protectedComment);
    }

    @Test
    void publicRegistrationIgnoresExistingIdAndAlwaysCreatesViewer() throws Exception {
        String body = "{\"id\":" + protectedUser.getId()
            + ",\"name\":\"Attacker\",\"email\":\"new-registration@mass-assignment.test\","
            + "\"password\":\"new-password\",\"role\":\"ADMIN\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("VIEWER"))
            .andExpect(jsonPath("$.password").doesNotExist());

        User unchanged = userRepository.findById(protectedUser.getId()).orElseThrow();
        assertEquals("protected@mass-assignment.test", unchanged.getEmail());
        assertEquals("VIEWER", unchanged.getRole());
        User created = userRepository.findByEmail("new-registration@mass-assignment.test").orElseThrow();
        assertNotEquals(protectedUser.getId(), created.getId());
        assertEquals("VIEWER", created.getRole());
        assertTrue(passwordEncoder.matches("new-password", created.getPassword()));
    }

    @Test
    void adminUserCreationIgnoresExistingId() throws Exception {
        String body = "{\"id\":" + protectedUser.getId()
            + ",\"name\":\"New Developer\",\"email\":\"new-developer@mass-assignment.test\","
            + "\"password\":\"new-password\",\"role\":\"DEVELOPER\"}";
        mockMvc.perform(post("/api/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.password").doesNotExist());

        User unchanged = userRepository.findById(protectedUser.getId()).orElseThrow();
        assertEquals("protected@mass-assignment.test", unchanged.getEmail());
        assertEquals("VIEWER", unchanged.getRole());
        assertNotEquals(protectedUser.getId(),
            userRepository.findByEmail("new-developer@mass-assignment.test").orElseThrow().getId());
    }

    @Test
    void projectCreationIgnoresExistingId() throws Exception {
        String body = "{\"id\":" + protectedProject.getId()
            + ",\"name\":\"Attacker project\",\"description\":\"new\"}";
        mockMvc.perform(post("/api/projects").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        assertEquals("Protected project", projectRepository.findById(protectedProject.getId()).orElseThrow().getName());
        Project created = projectRepository.findAll().stream()
            .filter(project -> "Attacker project".equals(project.getName())).findFirst().orElseThrow();
        assertNotEquals(protectedProject.getId(), created.getId());
    }

    @Test
    void sprintCreationIgnoresExistingIdAndResolvesProjectReference() throws Exception {
        String body = "{\"id\":" + protectedSprint.getId()
            + ",\"name\":\"Attacker sprint\",\"project\":{\"id\":" + protectedProject.getId() + "}}";
        mockMvc.perform(post("/api/sprints").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        assertEquals("Protected sprint", sprintRepository.findById(protectedSprint.getId()).orElseThrow().getName());
        Sprint created = sprintRepository.findAll().stream()
            .filter(sprint -> "Attacker sprint".equals(sprint.getName())).findFirst().orElseThrow();
        assertNotEquals(protectedSprint.getId(), created.getId());
        assertEquals(protectedProject.getId(), created.getProject().getId());
    }

    @Test
    void issueCreationIgnoresExistingIdAndUsesServerOwnership() throws Exception {
        String body = "{\"id\":" + protectedIssue.getId()
            + ",\"title\":\"Attacker issue\",\"status\":\"OPEN\","
            + "\"project\":{\"id\":" + protectedProject.getId() + "},"
            + "\"assignedUser\":{\"id\":" + developer.getId() + "},"
            + "\"reportedBy\":{\"id\":" + protectedUser.getId() + "},"
            + "\"tester\":{\"id\":" + protectedUser.getId() + "},"
            + "\"createdAt\":\"2000-01-01T00:00:00\"}";
        mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        Issue unchanged = issueRepository.findById(protectedIssue.getId()).orElseThrow();
        assertEquals("Protected issue", unchanged.getTitle());
        assertEquals(admin.getId(), unchanged.getReportedBy().getId());
        Issue created = issueRepository.findAll().stream()
            .filter(issue -> "Attacker issue".equals(issue.getTitle())).findFirst().orElseThrow();
        assertNotEquals(protectedIssue.getId(), created.getId());
        assertEquals(admin.getId(), created.getReportedBy().getId());
        assertNull(created.getTester());
        assertNotEquals(LocalDateTime.of(2000, 1, 1, 0, 0), created.getCreatedAt());
    }

    @Test
    void commentCreationIgnoresExistingIdAndUsesAuthenticatedAuthorAndServerTime() throws Exception {
        String body = "{\"id\":" + protectedComment.getId()
            + ",\"content\":\"Attacker comment\",\"issue\":{\"id\":" + protectedIssue.getId() + "},"
            + "\"user\":{\"id\":" + protectedUser.getId() + "},"
            + "\"createdAt\":\"2000-01-01T00:00:00\"}";
        mockMvc.perform(post("/api/comments").header("Authorization", bearer(developer))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        Comment unchanged = commentRepository.findById(protectedComment.getId()).orElseThrow();
        assertEquals("Protected comment", unchanged.getContent());
        assertEquals(developer.getId(), unchanged.getUser().getId());
        Comment created = commentRepository.findAll().stream()
            .filter(comment -> "Attacker comment".equals(comment.getContent())).findFirst().orElseThrow();
        assertNotEquals(protectedComment.getId(), created.getId());
        assertEquals(developer.getId(), created.getUser().getId());
        assertNotEquals(LocalDateTime.of(2000, 1, 1, 0, 0), created.getCreatedAt());
    }

    @Test
    void createEndpointsRejectInvalidRelationshipIdsAndNonDeveloperAssignees() throws Exception {
        long issueCount = issueRepository.count();
        mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Bad project reference\",\"project\":{\"id\":999999}}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/issues").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Bad assignee role\",\"assignedUser\":{\"id\":"
                    + protectedUser.getId() + "}}"))
            .andExpect(status().isBadRequest());
        assertEquals(issueCount, issueRepository.count());

        mockMvc.perform(post("/api/sprints").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad project reference\",\"project\":{\"id\":999999}}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/comments").header("Authorization", bearer(developer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Bad issue reference\",\"issue\":{\"id\":999999}}"))
            .andExpect(status().isNotFound());
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
