package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.entity.GitHubIssueActivity;
import com.devflow.devflow.entity.GitHubWebhookDelivery;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.GitHubIssueActivityRepository;
import com.devflow.devflow.repository.GitHubWebhookDeliveryRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.service.AuditLogServices;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class IssueActivityIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private AuditLogsRepository auditLogsRepository;
    @Autowired private GitHubIssueActivityRepository githubActivityRepository;
    @Autowired private GitHubWebhookDeliveryRepository githubDeliveryRepository;
    @Autowired private AuditLogServices auditLogServices;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User actor;
    private Issue issue;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setName("Activity Actor");
        actor.setEmail("activity-actor@example.test");
        actor.setPassword("unused-test-password");
        actor.setRole("PROJECT_MANAGER");
        actor = userRepository.saveAndFlush(actor);

        issue = new Issue();
        issue.setTitle("Activity issue");
        issue.setDescription("Initial description");
        issue.setPriority("MEDIUM");
        issue.setStatus("OPEN");
        issue = issueRepository.saveAndFlush(issue);
    }

    @Test
    void projectManagerCanReadSafeChronologicalIssueActivity() throws Exception {
        auditLogServices.createLog("ISSUE_CREATED", "Created", issue.getId(), actor.getId());

        mockMvc.perform(get("/api/issues/{id}/activity", issue.getId())
                .header("Authorization", bearer(actor)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].issueId").value(issue.getId()))
            .andExpect(jsonPath("$[0].action").value("ISSUE_CREATED"))
            .andExpect(jsonPath("$[0].actorId").value(actor.getId()))
            .andExpect(jsonPath("$[0].actorName").value("Activity Actor"))
            .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$[0].user").doesNotExist())
            .andExpect(jsonPath("$[0].actorPassword").doesNotExist());
    }

    @Test
    void updateAndCommentEventsUseTheAuthenticatedActor() throws Exception {
        Project project = new Project();
        project.setName("Activity project");
        project = projectRepository.saveAndFlush(project);

        Sprint sprint = new Sprint();
        sprint.setName("Activity sprint");
        sprint.setProject(project);
        sprint = sprintRepository.saveAndFlush(sprint);

        User assignee = new User();
        assignee.setName("Assigned Developer");
        assignee.setEmail("assigned-developer@example.test");
        assignee.setPassword("unused-test-password");
        assignee.setRole("DEVELOPER");
        assignee = userRepository.saveAndFlush(assignee);

        mockMvc.perform(put("/api/issues/{id}", issue.getId())
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Updated title","description":"Updated description","priority":"HIGH",
                    "status":"IN_PROGRESS","project":{"id":%d},"sprint":{"id":%d},"assignedUser":{"id":%d}}
                    """.formatted(project.getId(), sprint.getId(), assignee.getId())))
            .andExpect(status().isOk());

        User commentAuthor = new User();
        commentAuthor.setName("Comment Author");
        commentAuthor.setEmail("comment-author@example.test");
        commentAuthor.setPassword("unused-test-password");
        commentAuthor.setRole("DEVELOPER");
        commentAuthor = userRepository.saveAndFlush(commentAuthor);

        mockMvc.perform(post("/api/comments")
                .header("Authorization", bearer(commentAuthor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"Comment for the activity feed","issue":{"id":%d},"user":{"id":%d}}
                    """.formatted(issue.getId(), actor.getId())))
            .andExpect(status().isCreated());

        MvcResult activityResult = mockMvc.perform(get("/api/issues/{id}/activity", issue.getId())
                .header("Authorization", bearer(commentAuthor)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode entries = objectMapper.readTree(activityResult.getResponse().getContentAsString());
        assertTrue(hasEntry(entries, "TITLE_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "DESCRIPTION_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "PRIORITY_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "STATUS_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "PROJECT_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "SPRINT_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "ASSIGNED_USER_CHANGED", "Activity Actor"));
        assertTrue(hasEntry(entries, "COMMENT_ADDED", "Comment Author"));
        assertTrue(auditLogsRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId()).size() >= 4);
    }

    @Test
    void issueActivityRequiresAuthenticationAndMissingIssueIsNotFound() throws Exception {
        mockMvc.perform(get("/api/issues/{id}/activity", issue.getId()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/issues/{id}/timeline", issue.getId()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/issues/{id}/activity", Long.MAX_VALUE)
                .header("Authorization", bearer(actor)))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/issues/{id}/timeline", Long.MAX_VALUE)
                .header("Authorization", bearer(actor)))
            .andExpect(status().isNotFound());
    }

    @Test
    void taskTimelineUsesAuditHistoryAndDoesNotSurfaceLegacyGitHubActivity() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusMinutes(2).withNano(0);
        saveAudit("ISSUE_CREATED", base);

        GitHubWebhookDelivery delivery = new GitHubWebhookDelivery();
        delivery.setDeliveryId(java.util.UUID.randomUUID().toString());
        delivery.setEventType("push");
        delivery.setRepositoryOwner("acme");
        delivery.setRepositoryName("devflow");
        delivery.setReceivedAt(base.plusSeconds(1));
        delivery = githubDeliveryRepository.saveAndFlush(delivery);

        GitHubIssueActivity commit = new GitHubIssueActivity();
        commit.setIssue(issue);
        commit.setDelivery(delivery);
        commit.setActivityType("COMMIT");
        commit.setExternalId("abc123");
        commit.setSummary("Fix validation edge case");
        commit.setHtmlUrl("https://github.com/acme/devflow/commit/abc123");
        commit.setAuthor("octocat");
        commit.setEventAt(asGitHubUtc(base.plusSeconds(1)));
        githubActivityRepository.saveAndFlush(commit);

        saveAudit("GITHUB_ISSUE_RECONCILED", base.plusSeconds(2));

        mockMvc.perform(get("/api/issues/{id}/timeline", issue.getId())
                .header("Authorization", bearer(actor)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sourceType").value("AUDIT"))
            .andExpect(jsonPath("$[0].action").value("ISSUE_CREATED"))
            .andExpect(jsonPath("$[0].actorName").value("Activity Actor"))
            .andExpect(jsonPath("$[1].action").value("GITHUB_ISSUE_RECONCILED"))
            .andExpect(jsonPath("$[1].sourceType").value("AUDIT"))
            .andExpect(jsonPath("$[3]").doesNotExist())
            .andExpect(jsonPath("$[0].actorPassword").doesNotExist());
    }

    @Test
    void auditEventsWithEqualTimestampsKeepNumericDatabaseOrder() throws Exception {
        LocalDateTime sameTime = LocalDateTime.now().minusMinutes(1).withNano(0);
        for (int index = 1; index <= 12; index++) {
            saveAudit("TIMELINE_ORDER_" + index, sameTime);
        }

        MvcResult result = mockMvc.perform(get("/api/issues/{id}/timeline", issue.getId())
                .header("Authorization", bearer(actor)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(12, entries.size());
        for (int index = 0; index < 12; index++) {
            assertEquals("TIMELINE_ORDER_" + (index + 1), entries.get(index).path("action").asText());
        }
    }

    @Test
    void emptyTimelineAndMissingActorAreSafe() throws Exception {
        mockMvc.perform(get("/api/issues/{id}/timeline", issue.getId())
                .header("Authorization", bearer(actor)))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        AuditLog actorless = new AuditLog();
        actorless.setIssue(issue);
        actorless.setAction("ISSUE_CREATED");
        actorless.setDetails(null);
        actorless.setCreatedAt(LocalDateTime.now());
        auditLogsRepository.saveAndFlush(actorless);

        mockMvc.perform(get("/api/issues/{id}/timeline", issue.getId())
                .header("Authorization", bearer(actor)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].actorName").doesNotExist())
            .andExpect(jsonPath("$[0].details").doesNotExist());
    }

    @Test
    void auditSearchFiltersLoginFailuresAndUsesStableIdOrdering() {
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        saveAudit("LOGIN_FAILED", createdAt);
        saveAudit("LOGIN_FAILED", createdAt);
        saveAudit("LOGIN_SUCCEEDED", createdAt);

        var page = auditLogsRepository.searchAuditLogs(actor.getId(), "login_failed", null,
                "FAILURE", createdAt.minusSeconds(1), createdAt.plusSeconds(1),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().get(0).getId() > page.getContent().get(1).getId());
    }

    private void saveAudit(String action, LocalDateTime createdAt) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setCreatedAt(createdAt);
        log.setDetails("test event");
        log.setIssue(issue);
        log.setUser(actor);
        auditLogsRepository.saveAndFlush(log);
    }

    private LocalDateTime asGitHubUtc(LocalDateTime localTime) {
        return localTime.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }

    private boolean hasEntry(JsonNode entries, String action, String actorName) {
        for (JsonNode entry : entries) {
            if (action.equals(entry.path("action").asText())
                    && actorName.equals(entry.path("actorName").asText())) {
                return true;
            }
        }
        return false;
    }
}
