package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueApproval;
import com.devflow.devflow.entity.IssueMaintenance;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.IssueApprovalRepository;
import com.devflow.devflow.repository.IssueMaintenanceRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.NotificationRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssueApprovalWorkflowIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private ProjectRepository projects;
    @Autowired private IssueRepository issues;
    @Autowired private TestExecutionRepository executions;
    @Autowired private IssueApprovalRepository approvals;
    @Autowired private IssueMaintenanceRepository maintenance;
    @Autowired private AuthorizationRoleRepository roles;
    @Autowired private ProjectRoleAssignmentRepository projectRoles;
    @Autowired private TeamRoleAssignmentRepository teamRoles;
    @Autowired private NotificationRepository notifications;

    @Test
    void passLeadAndManagerStagesPublishScopedNotificationsAndPendingCounts() throws Exception {
        Fixture f = fixture();
        f.issue.setStatus("TESTING");
        issues.saveAndFlush(f.issue);
        TestExecution active = new TestExecution();
        active.setIssue(f.issue); active.setTester(f.tester); active.setWorkflowCycle(1);
        active.setStatus("IN_PROGRESS"); active.setStartedAt(java.time.LocalDateTime.now());
        active.setCreatedAt(java.time.LocalDateTime.now());
        executions.saveAndFlush(active);

        mvc.perform(post("/api/testing/{id}/complete", f.issue.getId())
                .header("Authorization", bearer(f.tester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"PASSED\",\"notes\":\"Acceptance checks passed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PASSED"));
        assertEquals("TEAM_LEAD_APPROVAL", issues.findById(f.issue.getId()).orElseThrow().getStatus());
        assertEquals(1, notifications.findByUserIdOrderByCreatedAtDesc(f.lead.getId()).stream()
            .filter(n -> "TEST_PASSED_AWAITING_TEAM_LEAD".equals(n.getType())).count());

        mvc.perform(get("/api/approvals/pending").header("Authorization", bearer(f.lead)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.teamLeadPendingCount").value(1))
            .andExpect(jsonPath("$.projectManagerPendingCount").value(0))
            .andExpect(jsonPath("$.pendingApprovals[0].taskName").value("Search bug"))
            .andExpect(jsonPath("$.pendingApprovals[0].projectName").value(org.hamcrest.Matchers.startsWith("project-")))
            .andExpect(jsonPath("$.pendingApprovals[0].developerName").value(f.developer.getName()))
            .andExpect(jsonPath("$.pendingApprovals[0].testerName").value(f.tester.getName()))
            .andExpect(jsonPath("$.pendingApprovals[0].testingResult").value("PASSED"))
            .andExpect(jsonPath("$.pendingApprovals[0].testingCompletedAt").isNotEmpty())
            .andExpect(jsonPath("$.pendingApprovals[0].waitingSeconds").isNumber());
        mvc.perform(get("/api/approvals/pending").header("Authorization", bearer(f.developer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pendingApprovals").isEmpty());

        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.lead)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isOk());
        assertEquals(1, notifications.findByUserIdOrderByCreatedAtDesc(f.manager.getId()).stream()
            .filter(n -> "PROJECT_MANAGER_APPROVAL".equals(n.getType())).count());
        mvc.perform(get("/api/approvals/pending").header("Authorization", bearer(f.manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.teamLeadPendingCount").value(0))
            .andExpect(jsonPath("$.projectManagerPendingCount").value(1))
            .andExpect(jsonPath("$.pendingApprovals[0].teamLeadName").value(f.lead.getName()))
            .andExpect(jsonPath("$.pendingApprovals[0].teamLeadApprovedAt").isNotEmpty());

        mvc.perform(post("/api/issues/{id}/approvals/PROJECT_MANAGER", f.issue.getId())
                .header("Authorization", bearer(f.manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isOk());
        assertEquals("RESOLVED", issues.findById(f.issue.getId()).orElseThrow().getStatus());
        assertEquals(1, notifications.findByUserIdOrderByCreatedAtDesc(f.developer.getId()).stream()
            .filter(n -> "APPROVAL_RESOLVED".equals(n.getType())).count());
        assertEquals(1, notifications.findByUserIdOrderByCreatedAtDesc(f.tester.getId()).stream()
            .filter(n -> "APPROVAL_RESOLVED".equals(n.getType())).count());
        mvc.perform(get("/api/approvals/pending").header("Authorization", bearer(f.manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.projectManagerPendingCount").value(0));
    }

    @Test
    void pendingApprovalsRequiresAuthenticationAndDoesNotExposeOtherStagesToDeveloper() throws Exception {
        Fixture f = fixture();
        passedTest(f);
        mvc.perform(get("/api/approvals/pending"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/approvals/pending").header("Authorization", bearer(f.developer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.teamLeadPendingCount").value(0))
            .andExpect(jsonPath("$.projectManagerPendingCount").value(0))
            .andExpect(jsonPath("$.pendingApprovals").isEmpty());
    }

    @Test
    void sequentialApprovalsResolveAndStartMaintenanceThenFollowUpKeepsOriginalResolved() throws Exception {
        Fixture f = fixture();
        passedTest(f);

        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.lead)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("TEAM_LEAD"))
            .andExpect(jsonPath("$.decision").value("APPROVED"));
        assertEquals("PROJECT_MANAGER_APPROVAL", issues.findById(f.issue.getId()).orElseThrow().getStatus());

        mvc.perform(post("/api/issues/{id}/approvals/PROJECT_MANAGER", f.issue.getId())
                .header("Authorization", bearer(f.manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("PROJECT_MANAGER"));
        assertEquals("RESOLVED", issues.findById(f.issue.getId()).orElseThrow().getStatus());
        IssueMaintenance window = maintenance.findByIssueId(f.issue.getId()).orElseThrow();
        assertEquals("ACTIVE", window.getStatus());

        mvc.perform(post("/api/issues/{id}/maintenance-follow-ups", f.issue.getId())
                .header("Authorization", bearer(f.manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Regression reported after release\",\"responsibleDeveloperId\":"
                    + f.developer.getId() + "}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Maintenance follow-up: Search bug"));
        mvc.perform(get("/api/issues/{id}/maintenance", f.issue.getId()).header("Authorization", bearer(f.developer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.followUps.length()").value(1))
            .andExpect(jsonPath("$.followUps[0].reason").value("Regression reported after release"));
        assertEquals("RESOLVED", issues.findById(f.issue.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectionRequiresRemarkReturnsToDeveloperAndRetainsCycleHistory() throws Exception {
        Fixture f = fixture();
        passedTest(f);
        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.lead)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\"}"))
            .andExpect(status().isBadRequest());
        assertEquals("TEAM_LEAD_APPROVAL", issues.findById(f.issue.getId()).orElseThrow().getStatus());

        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.lead)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\",\"remark\":\"Acceptance criteria are incomplete\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.remark").value("Acceptance criteria are incomplete"));
        assertEquals(1, notifications.findByUserIdOrderByCreatedAtDesc(f.developer.getId()).stream()
            .filter(n -> "APPROVAL_REJECTED".equals(n.getType())
                && n.getMessage().contains("Acceptance criteria are incomplete")).count());
        Issue returned = issues.findById(f.issue.getId()).orElseThrow();
        assertEquals("IN_PROGRESS", returned.getStatus());
        assertEquals(2, returned.getWorkflowCycle());
        assertEquals(f.developer.getId(), returned.getAssignedUser().getId());
        assertEquals(1, approvals.findByIssueIdOrderByWorkflowCycleAscDecidedAtAscIdAsc(f.issue.getId()).size());
    }

    @Test
    void onlyScopedApproversCanDecideAndAStageCannotBeDecidedTwice() throws Exception {
        Fixture f = fixture();
        passedTest(f);
        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.developer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.lead)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/issues/{id}/approvals/TEAM_LEAD", f.issue.getId())
                .header("Authorization", bearer(f.lead)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\",\"remark\":\"late duplicate\"}"))
            .andExpect(status().isConflict());
        assertEquals(1, approvals.findByIssueIdOrderByWorkflowCycleAscDecidedAtAscIdAsc(f.issue.getId()).size());
        assertEquals(1, notifications.findByUserIdOrderByCreatedAtDesc(f.manager.getId()).stream()
            .filter(n -> "PROJECT_MANAGER_APPROVAL".equals(n.getType())).count());
    }

    private void passedTest(Fixture f) {
        TestExecution execution = new TestExecution();
        execution.setIssue(f.issue); execution.setTester(f.tester); execution.setWorkflowCycle(1);
        execution.setStatus("COMPLETED"); execution.setResult("PASSED");
        executions.saveAndFlush(execution);
    }

    private Fixture fixture() {
        User manager = user("manager", "PROJECT_MANAGER");
        User lead = user("lead", "DEVELOPER");
        User developer = user("developer", "DEVELOPER");
        User tester = user("tester", "TESTER");
        Team team = new Team(); team.setName("team-" + System.nanoTime()); team.setManager(manager);
        team.setMembers(new java.util.LinkedHashSet<>(java.util.List.of(manager, lead, developer, tester)));
        team = teams.saveAndFlush(team);
        Project project = new Project(); project.setName("project-" + System.nanoTime()); project.setTeam(team);
        project = projects.saveAndFlush(project); team.setProject(project);
        assign(project, manager, "PROJECT_MANAGER_PROJECT", PermissionScope.PROJECT);
        assign(project, lead, "TEAM_LEAD_PROJECT", PermissionScope.PROJECT);
        assign(project, developer, "DEVELOPER_PROJECT", PermissionScope.PROJECT);
        assign(project, tester, "TESTER_PROJECT", PermissionScope.PROJECT);
        assign(team, manager, "PROJECT_MANAGER_TEAM", PermissionScope.PROJECT_TEAM);
        assign(team, lead, "TEAM_LEAD_TEAM", PermissionScope.PROJECT_TEAM);
        assign(team, developer, "DEVELOPER_TEAM", PermissionScope.PROJECT_TEAM);
        assign(team, tester, "TESTER_TEAM", PermissionScope.PROJECT_TEAM);
        Issue issue = new Issue(); issue.setTitle("Search bug"); issue.setStatus("TEAM_LEAD_APPROVAL");
        issue.setProject(project); issue.setAssignedUser(developer); issue.setTester(tester); issue.setReportedBy(developer);
        issue = issues.saveAndFlush(issue);
        return new Fixture(manager, lead, developer, tester, issue);
    }

    private void assign(Project project, User user, String code, PermissionScope scope) {
        projectRoles.save(new ProjectRoleAssignment(project, user, role(code, scope)));
    }
    private void assign(Team team, User user, String code, PermissionScope scope) {
        teamRoles.save(new TeamRoleAssignment(team, user, role(code, scope)));
    }
    private AuthorizationRole role(String code, PermissionScope scope) {
        AuthorizationRole role = roles.findByCodeAndRetiredFalse(code).orElseThrow();
        assertEquals(scope, role.getScope()); return role;
    }
    private User user(String prefix, String role) {
        User user = new User(); user.setName(prefix); user.setEmail(prefix + "-" + System.nanoTime() + "@approval.test");
        user.setRole(role); user.setEnabled(true); return users.saveAndFlush(user);
    }
    private String bearer(User user) { return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole()); }
    private record Fixture(User manager, User lead, User developer, User tester, Issue issue) {}
}
