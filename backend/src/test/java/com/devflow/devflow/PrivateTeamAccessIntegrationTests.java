package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.IssueDependency;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrivateTeamAccessIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private AuditLogsRepository auditLogsRepository;
    @Autowired private IssueDependencyRepository issueDependencyRepository;
    @Autowired private AuthorizationRoleRepository roleRepository;
    @Autowired private ProjectRoleAssignmentRepository projectRoleAssignments;
    @Autowired private TeamRoleAssignmentRepository teamRoleAssignments;

    @Test
    void managerCreatesTeamAndAddsOnlyRegisteredDevelopers() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User developer = user("DEVELOPER");
        User lead = user("DEVELOPER");
        User tester = user("TESTER");
        User extraDeveloper = user("DEVELOPER");

        mockMvc.perform(post("/api/teams").header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + unique("Missing project") + "\"}"))
            .andExpect(status().isBadRequest());
        String projectJson = mockMvc.perform(post("/api/projects").header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + unique("Core project") + "\",\"teamName\":\"" + unique("Core") + "\",\"teamComposition\":{\"managerUserId\":" + manager.getId() + ",\"projectManagerIds\":[" + manager.getId() + "],\"teamLeadIds\":[" + lead.getId() + "],\"developerIds\":[" + developer.getId() + "],\"testerIds\":[" + tester.getId() + "]}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.teamId").isNumber())
            .andReturn().getResponse().getContentAsString();
        long teamId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(projectJson).get("teamId").asLong();
        mockMvc.perform(get("/api/teams/{id}", teamId).header("Authorization", bearer(manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.manager.id").value(manager.getId()))
            .andExpect(jsonPath("$.members.length()").value(4));

        mockMvc.perform(post("/api/teams/{id}/members", teamId).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"developerId\":" + extraDeveloper.getId() + ",\"responsibility\":\"DEVELOPER_TEAM\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(5));
        mockMvc.perform(post("/api/teams/{id}/members", teamId).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"developerId\":" + user("VIEWER").getId() + ",\"responsibility\":\"VIEWER_TEAM\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/teams/{id}/members/{memberId}", teamId, manager.getId())
                .header("Authorization", bearer(manager)))
            .andExpect(status().isConflict());
    }

    @Test
    void managerCannotRemoveTheOnlyActiveDeveloperFromAnActiveProjectTeam() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User lead = user("DEVELOPER");
        User developer = user("DEVELOPER");
        User tester = user("TESTER");
        String response = mockMvc.perform(post("/api/projects").header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + unique("Complete project") + "\",\"teamComposition\":{\"managerUserId\":"
                    + manager.getId() + ",\"projectManagerIds\":[" + manager.getId() + "],\"teamLeadIds\":["
                    + lead.getId() + "],\"developerIds\":[" + developer.getId() + "],\"testerIds\":[" + tester.getId() + "]}}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number projectIdNumber = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        Long projectId = projectIdNumber.longValue();
        Long teamId = projectRepository.findById(projectId).orElseThrow().getTeam().getId();

        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}", teamId, developer.getId())
                .header("Authorization", bearer(manager)))
            .andExpect(status().isConflict());
        assertTrue(teamRepository.findDetailedById(teamId).orElseThrow().getMembers().stream()
            .anyMatch(member -> member.getId().equals(developer.getId())));
    }

    @Test
    void privateProjectAndItsTasksAreVisibleOnlyToTeamMembersAndAdministrators() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User secondManager = user("PROJECT_MANAGER");
        User developer = user("DEVELOPER");
        User outsider = user("DEVELOPER");
        User admin = user("ADMIN");
        Team team = team(manager, developer);
        Team otherTeam = team(secondManager);
        Project project = project(team);
        Project otherProject = project(otherTeam);
        Issue visibleIssue = issue(project, developer);
        Issue privateIssue = issue(otherProject, outsider);

        mockMvc.perform(get("/api/projects/{id}", project.getId()).header("Authorization", bearer(developer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(team.getId()));
        mockMvc.perform(get("/api/projects/{id}", otherProject.getId()).header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/teams/{id}", otherTeam.getId()).header("Authorization", bearer(manager)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/teams/{id}", team.getId()).header("Authorization", bearer(developer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[0].email").doesNotExist())
            .andExpect(jsonPath("$.manager.email").doesNotExist());
        mockMvc.perform(get("/api/projects").header("Authorization", bearer(developer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %s)]".formatted(otherProject.getId())).doesNotExist());
        mockMvc.perform(get("/api/issues/{id}", privateIssue.getId()).header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/issues/{id}", privateIssue.getId()).header("Authorization", bearer(developer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"cross-team update\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/issues/{id}/timeline", privateIssue.getId()).header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/testing/issue/{id}/history", privateIssue.getId())
                .header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/overview?projectId={id}", otherProject.getId())
                .header("Authorization", bearer(developer)))
            .andExpect(status().isForbidden());
        String queue = mockMvc.perform(get("/api/testing/queue").header("Authorization", bearer(manager)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(queue.contains("\"id\":" + privateIssue.getId() + ",\"title\""));
        mockMvc.perform(post("/api/comments").header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"should not cross teams\",\"issue\":{\"id\":" + privateIssue.getId() + "}}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/comments").header("Authorization", bearer(developer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"should not cross teams\",\"issue\":{\"id\":" + privateIssue.getId() + "}}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/{id}", otherProject.getId()).header("Authorization", bearer(admin)))
            .andExpect(status().isOk());

        var issueList = mockMvc.perform(get("/api/issues").header("Authorization", bearer(developer)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(issueList.contains("\"id\":" + privateIssue.getId() + ",\"title\""));
        assertEquals(team.getId(), projectRepository.findById(project.getId()).orElseThrow().getTeam().getId());
        assertEquals(visibleIssue.getId(), issueRepository.findById(visibleIssue.getId()).orElseThrow().getId());
    }

    @Test
    void testerTeamMemberCanDiscoverProjectContextAndOnlyAssignedTesterCanExecuteWork() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User tester = user("TESTER");
        User peerTester = user("TESTER");
        User outsider = user("TESTER");
        Team team = team(manager, tester, peerTester);
        Team otherTeam = team(user("PROJECT_MANAGER"), user("DEVELOPER"));
        Project project = project(team);
        Project otherProject = project(otherTeam);

        Sprint sprint = new Sprint();
        sprint.setName(unique("Tester sprint"));
        sprint.setStatus("ACTIVE");
        sprint.setStartDate(java.time.LocalDate.now().minusDays(1));
        sprint.setEndDate(java.time.LocalDate.now().plusDays(7));
        sprint.setProject(project);
        sprint = sprintRepository.saveAndFlush(sprint);

        Issue teamTask = issue(project, manager);
        teamTask.setTester(null);
        teamTask.setSprint(sprint);
        teamTask = issueRepository.saveAndFlush(teamTask);
        Issue privateTask = issue(otherProject, user("DEVELOPER"));

        mockMvc.perform(get("/api/teams").header("Authorization", bearer(tester)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %s)]".formatted(team.getId())).exists())
            .andExpect(jsonPath("$[?(@.id == %s)]".formatted(otherTeam.getId())).doesNotExist());
        mockMvc.perform(get("/api/teams/{id}", team.getId()).header("Authorization", bearer(tester)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[?(@.id == %s)]".formatted(tester.getId())).exists());

        mockMvc.perform(get("/api/projects").header("Authorization", bearer(tester)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %s)]".formatted(project.getId())).exists())
            .andExpect(jsonPath("$[?(@.id == %s)]".formatted(otherProject.getId())).doesNotExist());
        mockMvc.perform(get("/api/projects/{id}", project.getId()).header("Authorization", bearer(tester)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/issues").header("Authorization", bearer(tester)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %s)]".formatted(teamTask.getId())).exists())
            .andExpect(jsonPath("$[?(@.id == %s)]".formatted(privateTask.getId())).doesNotExist());
        mockMvc.perform(get("/api/issues/{id}", teamTask.getId()).header("Authorization", bearer(tester)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/sprints").header("Authorization", bearer(tester)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %s)]".formatted(sprint.getId())).exists());
        mockMvc.perform(get("/api/sprints/{id}", sprint.getId()).header("Authorization", bearer(tester)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/issues/{id}/timeline", teamTask.getId()).header("Authorization", bearer(tester)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/projects/{id}", project.getId()).header("Authorization", bearer(tester)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/overview?projectId={id}", project.getId())
                .header("Authorization", bearer(tester)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{id}", project.getId()).header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/issues/{id}", teamTask.getId()).header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sprints/{id}", sprint.getId()).header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/projects/{id}", project.getId()).header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/overview?projectId={id}", project.getId())
                .header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/issues/{id}/tester", teamTask.getId()).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"testerId\":" + tester.getId() + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tester.id").value(tester.getId()));
        mockMvc.perform(get("/api/testing/queue").header("Authorization", bearer(tester)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %s)]".formatted(teamTask.getId())).exists());
        mockMvc.perform(get("/api/testing/my").header("Authorization", bearer(tester)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %s)]".formatted(teamTask.getId())).exists());

        mockMvc.perform(post("/api/testing/{id}/start", teamTask.getId())
                .header("Authorization", bearer(peerTester)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/testing/{id}/start", teamTask.getId())
                .header("Authorization", bearer(tester)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/testing/{id}/complete", teamTask.getId())
                .header("Authorization", bearer(tester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\",\"notes\":\"Reproduction confirmed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("FAILED"));
        assertEquals("IN_PROGRESS", issueRepository.findById(teamTask.getId()).orElseThrow().getStatus());
    }

    @Test
    void nonManagerCannotManageMembershipOrMoveProjectsAcrossTeams() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User secondManager = user("PROJECT_MANAGER");
        User member = user("DEVELOPER");
        Team team = team(manager, member);
        Team otherTeam = team(secondManager);
        Project project = project(team);

        User lead = user("DEVELOPER"), developer = user("DEVELOPER"), tester = user("TESTER");
        String composition = "\"teamComposition\":{\"managerUserId\":" + manager.getId() + ",\"projectManagerIds\":[" + manager.getId() + "],\"teamLeadIds\":[" + lead.getId() + "],\"developerIds\":[" + developer.getId() + "],\"testerIds\":[" + tester.getId() + "]}";
        mockMvc.perform(post("/api/projects").header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + unique("Managed project") + "\",\"teamId\":" + team.getId() + "," + composition + "}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/teams/{id}/members", team.getId()).header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"developerId\":" + user("DEVELOPER").getId() + "}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/{id}", project.getId()).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"teamId\":" + otherTeam.getId() + "}"))
            .andExpect(status().isConflict());
        mockMvc.perform(put("/api/projects/{id}", project.getId()).header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"teamId\":null}"))
            .andExpect(status().isForbidden());
        assertEquals(team.getId(), projectRepository.findById(project.getId()).orElseThrow().getTeam().getId());
    }

    @Test
    void assignmentRequiresTeamMembershipAndIssueResponsesDoNotLeakCrossTeamBlockers() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User member = user("DEVELOPER");
        User otherManager = user("PROJECT_MANAGER");
        User outsider = user("DEVELOPER");
        Team team = team(manager, member);
        Team otherTeam = team(otherManager, outsider);
        Project project = project(team);
        Project otherProject = project(otherTeam);
        Issue visibleIssue = issue(project, member);
        Issue privateBlocker = issue(otherProject, outsider);

        mockMvc.perform(put("/api/issues/{id}", visibleIssue.getId()).header("Authorization", bearer(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignedUser\":{\"id\":" + outsider.getId() + "}}"))
            .andExpect(status().isBadRequest());

        IssueDependency legacyCrossTeamDependency = new IssueDependency();
        legacyCrossTeamDependency.setBlockedIssue(visibleIssue);
        legacyCrossTeamDependency.setBlockingIssue(privateBlocker);
        legacyCrossTeamDependency.setCreatedBy(manager);
        legacyCrossTeamDependency.setReason("pre-existing cross-team relationship");
        issueDependencyRepository.saveAndFlush(legacyCrossTeamDependency);

        mockMvc.perform(get("/api/issues/{id}", visibleIssue.getId()).header("Authorization", bearer(member)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blockedBy").isEmpty());
    }

    @Test
    void teamActivityIncludesRealTaskAuditAndRemainsTeamScoped() throws Exception {
        User manager = user("PROJECT_MANAGER");
        User member = user("DEVELOPER");
        User outsider = user("DEVELOPER");
        Team team = team(manager, member);
        Team otherTeam = team(user("PROJECT_MANAGER"));
        Issue visibleIssue = issue(project(team), member);
        Issue privateIssue = issue(project(otherTeam), outsider);

        AuditLog visibleEvent = new AuditLog();
        visibleEvent.setAction("STATUS_CHANGED");
        visibleEvent.setDetails("IN_PROGRESS → READY_FOR_TESTING");
        visibleEvent.setCreatedAt(java.time.LocalDateTime.now());
        visibleEvent.setIssue(visibleIssue);
        visibleEvent.setUser(member);
        auditLogsRepository.saveAndFlush(visibleEvent);

        AuditLog privateEvent = new AuditLog();
        privateEvent.setAction("STATUS_CHANGED");
        privateEvent.setDetails("private task event");
        privateEvent.setCreatedAt(java.time.LocalDateTime.now().plusSeconds(1));
        privateEvent.setIssue(privateIssue);
        privateEvent.setUser(outsider);
        auditLogsRepository.saveAndFlush(privateEvent);

        mockMvc.perform(get("/api/teams/{id}/activity", team.getId()).header("Authorization", bearer(member)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].action").value("STATUS_CHANGED"))
            .andExpect(jsonPath("$[0].actorId").value(member.getId()))
            .andExpect(jsonPath("$[0].taskId").value(visibleIssue.getId()))
            .andExpect(jsonPath("$[0].taskTitle").value(visibleIssue.getTitle()));

        mockMvc.perform(get("/api/teams/{id}/activity", team.getId()).header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/teams/{id}/activity", otherTeam.getId()).header("Authorization", bearer(manager)))
            .andExpect(status().isForbidden());
    }

    private User user(String role) {
        String email = unique(role.toLowerCase()) + "@private-teams.test";
        User user = new User();
        user.setName(role);
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private Team team(User manager, User... members) {
        Team team = new Team();
        team.setName(unique("Team"));
        team.setManager(manager);
        java.util.Set<User> teamMembers = new java.util.LinkedHashSet<>();
        teamMembers.add(manager);
        teamMembers.addAll(java.util.List.of(members));
        team.setMembers(teamMembers);
        return teamRepository.saveAndFlush(team);
    }

    private Project project(Team team) {
        Project project = new Project();
        project.setName(unique("Project"));
        project.setTeam(team);
        project = projectRepository.saveAndFlush(project);
        for (User member : team.getMembers()) {
            String projectCode = switch (member.getRole()) {
                case "PROJECT_MANAGER" -> "PROJECT_MANAGER_PROJECT";
                case "DEVELOPER" -> "DEVELOPER_PROJECT";
                case "TESTER" -> "TESTER_PROJECT";
                default -> "VIEWER_PROJECT";
            };
            String teamCode = switch (member.getRole()) {
                case "PROJECT_MANAGER" -> "PROJECT_MANAGER_TEAM";
                case "DEVELOPER" -> "DEVELOPER_TEAM";
                case "TESTER" -> "TESTER_TEAM";
                default -> "VIEWER_TEAM";
            };
            projectRoleAssignments.save(new ProjectRoleAssignment(project, member, role(projectCode)));
            teamRoleAssignments.save(new TeamRoleAssignment(team, member, role(teamCode)));
        }
        return project;
    }

    private AuthorizationRole role(String code) {
        return roleRepository.findByCodeAndRetiredFalse(code).orElseThrow();
    }

    private Issue issue(Project project, User assignee) {
        Issue issue = new Issue();
        issue.setTitle(unique("Task"));
        issue.setStatus("READY_FOR_TESTING");
        issue.setProject(project);
        issue.setAssignedUser(assignee);
        issue.setTester(assignee);
        return issueRepository.saveAndFlush(issue);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail(), user.getRole());
    }

    private String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
