package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class IssueDependencyIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private IssueRepository issueRepository;
    @Autowired private UserRepository userRepository;

    private User manager;
    private User developer;
    private Issue blockedIssue;
    private Issue blockingIssue;

    @BeforeEach
    void setUp() {
        manager = saveUser("PROJECT_MANAGER", "dependency-manager@example.test");
        developer = saveUser("DEVELOPER", "dependency-developer@example.test");
        blockedIssue = saveIssue("Implement login", "OPEN");
        blockingIssue = saveIssue("Publish API contract", "IN_PROGRESS");
    }

    @Test
    void persistedDependenciesAppearInBothDirectionsAndReflectCurrentBlockerStatus() throws Exception {
        addBlocker(blockedIssue, blockingIssue, manager)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocked").value(true))
            .andExpect(jsonPath("$.blockedBy[0].issueId").value(blockingIssue.getId()))
            .andExpect(jsonPath("$.blockedBy[0].reason").value("Waiting for API contract"));

        mockMvc.perform(get("/api/issues/{id}", blockingIssue.getId())
                .header("Authorization", bearer(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks[0].issueId").value(blockedIssue.getId()));

        blockingIssue.setStatus("RESOLVED");
        issueRepository.saveAndFlush(blockingIssue);
        mockMvc.perform(get("/api/issues/{id}", blockedIssue.getId())
                .header("Authorization", bearer(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocked").value(false))
            .andExpect(jsonPath("$.blockedBy[0].status").value("RESOLVED"));
    }

    @Test
    void duplicateSelfAndCircularLinksAreRejectedAndManagerCanRemoveLink() throws Exception {
        addBlocker(blockedIssue, blockingIssue, manager).andExpect(status().isOk());
        addBlocker(blockedIssue, blockingIssue, manager).andExpect(status().isConflict());
        addBlocker(blockingIssue, blockedIssue, manager).andExpect(status().isConflict());

        mockMvc.perform(post("/api/issues/{id}/dependencies", blockedIssue.getId())
                .header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockingIssueId\":" + blockedIssue.getId() + ",\"reason\":\"self\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/issues/{id}/dependencies/{blockerId}",
                    blockedIssue.getId(), blockingIssue.getId())
                .header("Authorization", bearer(manager)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/issues/{id}", blockedIssue.getId())
                .header("Authorization", bearer(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocked").value(false))
            .andExpect(jsonPath("$.blockedBy").isEmpty());
    }

    @Test
    void developerCannotCreateOrRemoveBlockerLinks() throws Exception {
        addBlocker(blockedIssue, blockingIssue, manager).andExpect(status().isOk());
        mockMvc.perform(post("/api/issues/{id}/dependencies", blockedIssue.getId())
                .header("Authorization", bearer(developer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockingIssueId\":" + blockingIssue.getId() + ",\"reason\":\"Not allowed\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/issues/{id}/dependencies/{blockerId}",
                    blockedIssue.getId(), blockingIssue.getId())
                .header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions addBlocker(Issue blocked, Issue blocker, User actor)
            throws Exception {
        return mockMvc.perform(post("/api/issues/{id}/dependencies", blocked.getId())
            .header("Authorization", bearer(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"blockingIssueId\":" + blocker.getId()
                + ",\"reason\":\"Waiting for API contract\"}"));
    }

    private User saveUser(String role, String email) {
        User user = new User();
        user.setName(role + " User");
        user.setEmail(email);
        user.setPassword("unused-test-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Issue saveIssue(String title, String status) {
        Issue issue = new Issue();
        issue.setTitle(title);
        issue.setPriority("MEDIUM");
        issue.setStatus(status);
        return issueRepository.saveAndFlush(issue);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }
}
