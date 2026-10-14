package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardAnalyticsIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private IssueRepository issueRepository;

    @Test
    void viewerCanReadDatabaseBackedStatusProjectAndTeamMetrics() throws Exception {
        User viewer = saveUser("VIEWER");
        User developer = saveUser("DEVELOPER");
        User tester = saveUser("TESTER");
        Project project = new Project();
        project.setName("Analytics project " + UUID.randomUUID());
        project = projectRepository.saveAndFlush(project);

        Issue issue = new Issue();
        issue.setTitle("Analytics issue");
        issue.setStatus("RESOLVED");
        issue.setProject(project);
        issue.setAssignedUser(developer);
        issue.setTester(tester);
        issue.setReportedBy(viewer);
        issueRepository.saveAndFlush(issue);

        mockMvc.perform(get("/api/dashboard/summary")
                .header("Authorization", "Bearer " + jwtService.generateToken(viewer.getEmail(), viewer.getRole()))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalProjects").value(1))
            .andExpect(jsonPath("$.totalIssues").value(1))
            .andExpect(jsonPath("$.completedIssues").value(1))
            .andExpect(jsonPath("$.activeIssues").value(0))
            .andExpect(jsonPath("$.statusDistribution[0].status").value("RESOLVED"))
            .andExpect(jsonPath("$.statusDistribution[0].count").value(1))
            .andExpect(jsonPath("$.projectProgress[0].totalIssues").value(1))
            .andExpect(jsonPath("$.projectProgress[0].completionPercent").value(100))
            .andExpect(jsonPath("$.teamWorkload.length()").value(2));
    }

    private User saveUser(String role) {
        User user = new User();
        user.setName(role + " analytics");
        user.setEmail(UUID.randomUUID().toString().replace("-", "") + "@dashboard.test");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
