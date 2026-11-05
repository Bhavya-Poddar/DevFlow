package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
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
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.IssueDependency;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
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
    @Autowired private SprintRepository sprintRepository;
    @Autowired private TestExecutionRepository testExecutionRepository;
    @Autowired private IssueDependencyRepository issueDependencyRepository;

    @Test
    void nonAdminCannotReadUnfilteredDashboardSummary() throws Exception {
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
            .andExpect(status().isBadRequest());
    }

    @Test
    void filteredOverviewReportsSupportedStatusesAndPastSprintWork() throws Exception {
        User viewer = saveUser("VIEWER");
        User developer = saveUser("DEVELOPER");
        Project project = saveProject("Overview");
        Sprint ended = saveSprint(project, "Ended sprint", LocalDate.now(Clock.systemUTC()).minusDays(1));
        saveIssue(project, ended, developer, "IN_PROGRESS");
        saveIssue(project, ended, developer, "RESOLVED");

        mockMvc.perform(get("/api/dashboard/overview").param("projectId", project.getId().toString())
                .param("sprintId", ended.getId().toString()).header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTasks").value(2))
            .andExpect(jsonPath("$.completedTasks").value(1))
            .andExpect(jsonPath("$.inProgressTasks").value(1))
            .andExpect(jsonPath("$.overdueTasks").value(1))
            .andExpect(jsonPath("$.completionRate").value(50))
            .andExpect(jsonPath("$.overdueDefinition").exists())
            .andExpect(jsonPath("$.statusDistribution.length()").value(8));

        mockMvc.perform(get("/api/dashboard/sprints").param("projectId", project.getId().toString())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeSprints[0].totalTasks").value(2))
            .andExpect(jsonPath("$.activeSprints[0].completionRate").value(50));

        mockMvc.perform(get("/api/dashboard/workload").param("projectId", project.getId().toString())
                .param("sprintId", ended.getId().toString()).header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[0].role").value("DEVELOPER"))
            .andExpect(jsonPath("$.members[0].assignedIssues").value(2));
    }

    @Test
    void sprintEndingTodayIsNotYetCountedAsPastSprintWork() throws Exception {
        User viewer = saveUser("VIEWER");
        Project project = saveProject("Boundary");
        Sprint sprint = saveSprint(project, "Ends today", LocalDate.now(Clock.systemUTC()));
        saveIssue(project, sprint, null, "OPEN");

        mockMvc.perform(get("/api/dashboard/overview").param("projectId", project.getId().toString())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdueTasks").value(0));
    }

    @Test
    void testingInsightsUseCompletedPassFailResultsAsDenominator() throws Exception {
        User viewer = saveUser("VIEWER");
        Project project = saveProject("Testing metrics");
        Sprint sprint = saveSprint(project, "Current", LocalDate.now(Clock.systemUTC()).plusDays(5));
        Issue issue = saveIssue(project, sprint, null, "TESTING");
        saveExecution(issue, "COMPLETED", "PASSED");
        saveExecution(issue, "COMPLETED", "FAILED");
        saveExecution(issue, "IN_PROGRESS", null);

        mockMvc.perform(get("/api/dashboard/testing").param("projectId", project.getId().toString())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedExecutions").value(2))
            .andExpect(jsonPath("$.passedExecutions").value(1))
            .andExpect(jsonPath("$.failedExecutions").value(1))
            .andExpect(jsonPath("$.passRate").value(50))
            .andExpect(jsonPath("$.denominator").value(org.hamcrest.Matchers.containsString("PASSED or FAILED")));
    }

    @Test
    void bottlenecksReturnUnresolvedBlockerAndOverdueTaskLinks() throws Exception {
        User viewer = saveUser("VIEWER");
        User developer = saveUser("DEVELOPER");
        Project project = saveProject("Bottlenecks");
        Sprint ended = saveSprint(project, "Ended", LocalDate.now(Clock.systemUTC()).minusDays(2));
        Issue blocked = saveIssue(project, ended, developer, "IN_PROGRESS");
        Issue blocker = saveIssue(project, ended, null, "OPEN");
        IssueDependency dependency = new IssueDependency();
        dependency.setBlockedIssue(blocked);
        dependency.setBlockingIssue(blocker);
        dependency.setCreatedBy(viewer);
        dependency.setReason("Waiting on blocker");
        issueDependencyRepository.saveAndFlush(dependency);

        mockMvc.perform(get("/api/dashboard/bottlenecks").param("projectId", project.getId().toString())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blockedTasks[0].taskId").value(blocked.getId()))
            .andExpect(jsonPath("$.blockedTasks[0].blockingTaskId").value(blocker.getId()))
            .andExpect(jsonPath("$.overdueTasks.length()").value(2));
    }

    @Test
    void workloadRiskUsesInclusiveDeadlineWindowAndExcludesResolvedTasksFromLoad() throws Exception {
        User viewer = saveUser("VIEWER");
        User developer = saveUser("DEVELOPER");
        Project project = saveProject("Workload risk");
        LocalDate today = LocalDate.now(Clock.systemUTC());
        Sprint sprint = saveSprint(project, "Risk sprint", today.plusDays(7));

        Issue overdue = saveIssue(project, sprint, developer, "IN_PROGRESS");
        overdue.setDueDate(today.minusDays(1));
        issueRepository.saveAndFlush(overdue);
        Issue dueToday = saveIssue(project, sprint, developer, "OPEN");
        dueToday.setDueDate(today);
        issueRepository.saveAndFlush(dueToday);
        Issue dueAtWindowEnd = saveIssue(project, sprint, null, "READY_FOR_TESTING");
        dueAtWindowEnd.setDueDate(today.plusDays(7));
        issueRepository.saveAndFlush(dueAtWindowEnd);
        Issue resolved = saveIssue(project, sprint, developer, "RESOLVED");
        resolved.setDueDate(today.plusDays(2));
        issueRepository.saveAndFlush(resolved);
        Issue outsideWindow = saveIssue(project, sprint, developer, "OPEN");
        outsideWindow.setDueDate(today.plusDays(8));
        issueRepository.saveAndFlush(outsideWindow);

        mockMvc.perform(get("/api/dashboard/workload-risk").param("projectId", project.getId().toString())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.asOf").value(today.toString()))
            .andExpect(jsonPath("$.approachingDeadlineThrough").value(today.plusDays(7).toString()))
            .andExpect(jsonPath("$.overdueTasks.length()").value(1))
            .andExpect(jsonPath("$.overdueTasks[0].taskId").value(overdue.getId()))
            .andExpect(jsonPath("$.approachingDeadlineTasks.length()").value(2))
            .andExpect(jsonPath("$.approachingDeadlineTasks[0].taskId").value(dueToday.getId()))
            .andExpect(jsonPath("$.approachingDeadlineTasks[1].taskId").value(dueAtWindowEnd.getId()))
            .andExpect(jsonPath("$.unresolvedByAssignee[0].userId").value(developer.getId()))
            .andExpect(jsonPath("$.unresolvedByAssignee[0].unresolvedTasks").value(3))
            .andExpect(jsonPath("$.unresolvedByAssignee[1].name").value("Unassigned"))
            .andExpect(jsonPath("$.unresolvedByAssignee[1].unresolvedTasks").value(1))
            .andExpect(jsonPath("$.sprintRisks[0].totalTasks").value(5))
            .andExpect(jsonPath("$.sprintRisks[0].completedTasks").value(1))
            .andExpect(jsonPath("$.sprintRisks[0].unresolvedTasks").value(4))
            .andExpect(jsonPath("$.sprintRisks[0].overdueTasks").value(1))
            .andExpect(jsonPath("$.sprintRisks[0].approachingDeadlineTasks").value(2))
            .andExpect(jsonPath("$.sprintRisks[0].completionRate").value(20))
            .andExpect(jsonPath("$.sprintRisks[0].riskLevel").value("OVERDUE"));
    }

    @Test
    void emptyDataAndInvalidFilterRelationshipsAreHandled() throws Exception {
        User viewer = saveUser("VIEWER");
        Project project = saveProject("Empty dashboard");
        mockMvc.perform(get("/api/dashboard/testing").param("projectId", project.getId().toString())
                .header("Authorization", bearer(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedExecutions").value(0))
            .andExpect(jsonPath("$.passRate").value(0));

        Project other = saveProject("Other dashboard project");
        Sprint otherSprint = saveSprint(other, "Other sprint", LocalDate.now(Clock.systemUTC()).plusDays(3));
        mockMvc.perform(get("/api/dashboard/overview").param("projectId", project.getId().toString())
                .param("sprintId", otherSprint.getId().toString()).header("Authorization", bearer(viewer)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/dashboard/overview"))
            .andExpect(status().isUnauthorized());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }

    private Project saveProject(String prefix) {
        Project project = new Project();
        project.setName(prefix + " " + UUID.randomUUID());
        return projectRepository.saveAndFlush(project);
    }

    private Sprint saveSprint(Project project, String name, LocalDate endDate) {
        Sprint sprint = new Sprint();
        sprint.setName(name);
        sprint.setProject(project);
        sprint.setStartDate(endDate.minusDays(7));
        sprint.setEndDate(endDate);
        sprint.setStatus("ACTIVE");
        return sprintRepository.saveAndFlush(sprint);
    }

    private Issue saveIssue(Project project, Sprint sprint, User developer, String status) {
        Issue issue = new Issue();
        issue.setTitle("Dashboard task " + UUID.randomUUID());
        issue.setProject(project);
        issue.setSprint(sprint);
        issue.setAssignedUser(developer);
        issue.setStatus(status);
        return issueRepository.saveAndFlush(issue);
    }

    private void saveExecution(Issue issue, String status, String result) {
        TestExecution execution = new TestExecution();
        execution.setIssue(issue);
        execution.setStatus(status);
        execution.setResult(result);
        execution.setCreatedAt(java.time.LocalDateTime.now());
        testExecutionRepository.saveAndFlush(execution);
    }

    private User saveUser(String role) {
        User user = new User();
        user.setName(role + " analytics");
        user.setEmail(UUID.randomUUID().toString().replace("-", "") + "@dashboard.test");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
