package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.IssueWorkflowResponseDTO;
import com.devflow.devflow.dto.TestCompletionRequestDTO;
import com.devflow.devflow.dto.TestExecutionResponseDTO;
import com.devflow.devflow.dto.CollaboratorResponseDTO;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.entity.AutomationTriggerType;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.service.ProjectAccessService;

@Service
public class TestExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutionService.class);

    private final IssueRepository issueRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final UserRepository userRepository;
    private final AuditLogServices auditLogService;
    private final NotificationService notificationService;
    private final AutomationEventService automationEventService;
    private final ProjectAccessService projectAccessService;
    private final EffectivePermissionService permissions;
    private final ProjectRoleAssignmentRepository projectRoles;
    private final TeamRoleAssignmentRepository teamRoles;

    public TestExecutionService(IssueRepository issueRepository,
            TestExecutionRepository testExecutionRepository,
            UserRepository userRepository,
            AuditLogServices auditLogService,
            NotificationService notificationService,
            AutomationEventService automationEventService,
            ProjectAccessService projectAccessService,
            EffectivePermissionService permissions,
            ProjectRoleAssignmentRepository projectRoles,
            TeamRoleAssignmentRepository teamRoles) {
        this.issueRepository = issueRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.automationEventService = automationEventService;
        this.projectAccessService = projectAccessService;
        this.permissions = permissions;
        this.projectRoles = projectRoles;
        this.teamRoles = teamRoles;
    }

    public List<IssueWorkflowResponseDTO> getQueue() {
        return issueRepository.findByStatusAndTesterIsNotNullOrderByUpdatedAtAsc(IssueStatus.READY_FOR_TESTING.name())
            .stream().map(this::toIssueResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<IssueWorkflowResponseDTO> getQueue(String actorEmail) {
        return visibleToActor(issueRepository.findByStatusAndTesterIsNotNullOrderByUpdatedAtAsc(
            IssueStatus.READY_FOR_TESTING.name()), actorEmail);
    }

    @Transactional(readOnly = true)
    public List<IssueWorkflowResponseDTO> getMyQueue(String testerEmail) {
        User tester = requireTester(testerEmail);
        return visibleToActor(issueRepository.findByTesterIdAndStatusInOrderByUpdatedAtAsc(
                tester.getId(), List.of(IssueStatus.READY_FOR_TESTING.name(), IssueStatus.TESTING.name())), testerEmail);
    }

    @Transactional
    public TestExecutionResponseDTO startTesting(Long issueId, String testerEmail) {
        User tester = requireEnabledActor(testerEmail);
        Issue issue = getIssueForUpdate(issueId);
        projectAccessService.requireProjectAccess(issue.getProject(), testerEmail);
        permissions.require(testerEmail, "TEST_EXECUTE", projectId(issue), null);
        requireAssignedTesterOrAdmin(issue, tester);
        if (!IssueStatus.READY_FOR_TESTING.name().equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not ready for testing");
        }
        if (testExecutionRepository.existsByIssueIdAndStatus(issueId, "IN_PROGRESS")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "An active test execution already exists for this task");
        }

        LocalDateTime now = LocalDateTime.now();
        TestExecution execution = new TestExecution();
        execution.setIssue(issue);
        execution.setTester(tester);
        execution.setWorkflowCycle(issue.getWorkflowCycle());
        execution.setStatus("IN_PROGRESS");
        execution.setStartedAt(now);
        execution.setCreatedAt(now);
        TestExecution savedExecution = testExecutionRepository.save(execution);

        issue.setStatus(IssueStatus.TESTING.name());
        issueRepository.save(issue);
        automationEventService.enqueue(AutomationTriggerType.TASK_STATUS_CHANGED, issue,
            IssueStatus.READY_FOR_TESTING.name(), IssueStatus.TESTING.name(), "test-start:" + savedExecution.getId());
        auditLogService.createLog("TEST_STARTED",
            "targetType=ISSUE; targetId=" + issueId + "; testing started", issueId, tester.getId());
        notifyDeveloper(issue, tester, "TEST_STARTED", "Testing started for task '" + issue.getTitle() + "'");
        return toExecutionResponse(savedExecution);
    }

    @Transactional
    public TestExecutionResponseDTO completeTesting(Long issueId, TestCompletionRequestDTO request, String testerEmail) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test result is required");
        }
        User tester = requireEnabledActor(testerEmail);
        Issue issue = getIssueForUpdate(issueId);
        projectAccessService.requireProjectAccess(issue.getProject(), testerEmail);
        permissions.require(testerEmail, "TEST_EXECUTE", projectId(issue), null);
        requireAssignedTesterOrAdmin(issue, tester);
        if (!IssueStatus.TESTING.name().equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not being tested");
        }

        String result = request.getResult() == null ? "" : request.getResult().trim().toUpperCase(Locale.ROOT);
        if (!"PASSED".equals(result) && !"FAILED".equals(result)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Result must be PASSED or FAILED");
        }
        String notes = request.getNotes() == null ? null : request.getNotes().trim();
        if ("FAILED".equals(result) && (notes == null || notes.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failure notes are required");
        }

        long activeExecutionCount = testExecutionRepository.countByIssueIdAndStatus(issueId, "IN_PROGRESS");
        if (activeExecutionCount != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                activeExecutionCount == 0
                    ? "No active test execution exists"
                    : "Multiple active test executions exist; resolve the history before completing testing");
        }

        TestExecution execution = testExecutionRepository
            .findFirstByIssueIdAndTesterIdAndStatusOrderByCreatedAtDesc(issueId, tester.getId(), "IN_PROGRESS")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active test execution exists"));

        LocalDateTime completedAt = LocalDateTime.now();
        execution.setStatus("COMPLETED");
        execution.setResult(result);
        execution.setTestScenario(clean(request.getTestScenario()));
        execution.setExpectedResult(clean(request.getExpectedResult()));
        execution.setActualResult(clean(request.getActualResult()));
        execution.setNotes(notes);
        execution.setCompletedAt(completedAt);
        TestExecution savedExecution = testExecutionRepository.save(execution);

        issue.setStatus("PASSED".equals(result)
            ? IssueStatus.TEAM_LEAD_APPROVAL.name() : IssueStatus.IN_PROGRESS.name());
        if ("FAILED".equals(result)) issue.setWorkflowCycle(issue.getWorkflowCycle() + 1);
        issueRepository.save(issue);

        automationEventService.enqueue(AutomationTriggerType.TASK_STATUS_CHANGED, issue,
            IssueStatus.TESTING.name(), issue.getStatus(), "test-complete:" + savedExecution.getId());
        automationEventService.enqueue("PASSED".equals(result)
                ? AutomationTriggerType.TESTING_PASSED : AutomationTriggerType.TESTING_FAILED,
            issue, IssueStatus.TESTING.name(), issue.getStatus(), "test-execution:" + savedExecution.getId());

        String action = "PASSED".equals(result) ? "TEST_PASSED" : "TEST_FAILED";
        String details = buildOutcomeDetails(issue, result);
        auditLogService.createLog(action, details, issueId, tester.getId());
        if ("PASSED".equals(result)) {
            notifyApprovers(issue, tester, "ISSUE_APPROVE_TEAM_LEAD", "TEST_PASSED_AWAITING_TEAM_LEAD",
                "Testing passed. This task is waiting for your Team Lead approval.");
        } else {
            notifyDeveloper(issue, tester, "TEST_FAILED",
                "Testing failed for task '" + issue.getTitle() + "'. Remark: " + notes);
        }
        return toExecutionResponse(savedExecution);
    }

    public List<TestExecutionResponseDTO> getHistory(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return testExecutionRepository.findByIssueIdOrderByCreatedAtDesc(issueId)
            .stream().map(this::toExecutionResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TestExecutionResponseDTO> getHistory(Long issueId, String actorEmail) {
        projectAccessService.requireIssueAccess(issueId, actorEmail);
        return getHistory(issueId);
    }

    private List<IssueWorkflowResponseDTO> visibleToActor(List<Issue> issues, String actorEmail) {
        java.util.Set<Long> visibleProjectIds = new java.util.HashSet<>(
            projectAccessService.visibleProjectIds(actorEmail));
        return issues.stream()
            .filter(issue -> issue.getProject() == null || visibleProjectIds.contains(issue.getProject().getId()))
            .map(this::toIssueResponse).toList();
    }

    private User requireTester(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled() || !permissions.hasAnywhere(email, "TESTING_MY_READ")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Testing queue permission is not granted");
        }
        return user;
    }

    private User requireEnabledActor(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Disabled users cannot perform this action");
        }
        return user;
    }

    private Issue getIssueForUpdate(Long issueId) {
        return issueRepository.findByIdForUpdate(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private void requireAssignedTester(Issue issue, User tester) {
        if (issue.getTester() == null || !Objects.equals(issue.getTester().getId(), tester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task is assigned to another tester");
        }
    }

    private void requireAssignedTesterOrAdmin(Issue issue, User actor) {
        if (permissions.hasGlobal(actor.getEmail(), "TESTING_OVERRIDE")) return;
        requireAssignedTester(issue, actor);
    }

    private Long projectId(Issue issue) {
        return issue.getProject() == null ? null : issue.getProject().getId();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildOutcomeDetails(Issue issue, String result) {
        return "targetType=ISSUE; targetId=" + issue.getId() + "; testOutcome=" + result;
    }

    private void notifyDeveloper(Issue issue, User tester, String type, String message) {
        User developer = issue.getAssignedUser();
        if (developer == null || Objects.equals(developer.getId(), tester.getId()) || !developer.isEnabled()) return;
        notificationService.createNotification(developer, type, message, NotificationTargetPaths.issue(issue.getId()));
    }

    private void notifyApprovers(Issue issue, User tester, String permission, String type, String message) {
        if (issue.getProject() == null || issue.getProject().getId() == null) return;
        Long projectId = issue.getProject().getId();
        java.util.Map<Long, User> recipients = new java.util.LinkedHashMap<>();
        for (ProjectRoleAssignment assignment : projectRoles.findByProjectId(projectId)) {
            User user = assignment.getUser();
            if (user.isEnabled() && permissions.has(user.getEmail(), permission, projectId, null)) {
                recipients.put(user.getId(), user);
            }
        }
        if (issue.getProject().getTeam() != null) {
            for (TeamRoleAssignment assignment : teamRoles.findByTeamId(issue.getProject().getTeam().getId())) {
                User user = assignment.getUser();
                if (user.isEnabled() && permissions.has(user.getEmail(), permission, projectId, null)) {
                    recipients.put(user.getId(), user);
                }
            }
        }
        recipients.values().stream().filter(user -> !user.getId().equals(tester.getId()))
            .forEach(user -> notificationService.createNotification(user, type, message,
                NotificationTargetPaths.issue(issue.getId())));
    }

    private IssueWorkflowResponseDTO toIssueResponse(Issue issue) {
        return new IssueWorkflowResponseDTO(
            issue.getId(), issue.getTitle(), issue.getDescription(), issue.getAcceptanceCriteria(), issue.getPriority(), issue.getStatus(),
            issue.getProject() == null ? null : issue.getProject().getId(),
            issue.getProject() == null ? null : issue.getProject().getName(),
            toUserResponse(issue.getAssignedUser()), toUserResponse(issue.getTester()),
            toUserResponse(issue.getReportedBy()),
            issue.getSprint() == null ? null : issue.getSprint().getId(),
            issue.getSprint() == null ? null : issue.getSprint().getName(),
            issue.getCreatedAt(), issue.getUpdatedAt());
    }

    private TestExecutionResponseDTO toExecutionResponse(TestExecution execution) {
        return new TestExecutionResponseDTO(
            execution.getId(),
            execution.getIssue() == null ? null : execution.getIssue().getId(),
            toUserResponse(execution.getTester()),
            execution.getStatus(), execution.getResult(), execution.getTestScenario(),
            execution.getExpectedResult(), execution.getActualResult(), execution.getNotes(),
            execution.getStartedAt(), execution.getCompletedAt(), execution.getCreatedAt());
    }

    private CollaboratorResponseDTO toUserResponse(User user) {
        if (user == null) return null;
        return new CollaboratorResponseDTO(user);
    }
}
