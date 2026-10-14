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
import com.devflow.devflow.dto.UserResponseDTO;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class TestExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutionService.class);

    private final IssueRepository issueRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final UserRepository userRepository;
    private final AuditLogServices auditLogService;
    private final NotificationService notificationService;

    public TestExecutionService(IssueRepository issueRepository,
            TestExecutionRepository testExecutionRepository,
            UserRepository userRepository,
            AuditLogServices auditLogService,
            NotificationService notificationService) {
        this.issueRepository = issueRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public List<IssueWorkflowResponseDTO> getQueue() {
        return issueRepository.findByStatusAndTesterIsNotNullOrderByUpdatedAtAsc(IssueStatus.READY_FOR_TESTING.name())
            .stream().map(this::toIssueResponse).toList();
    }

    public List<IssueWorkflowResponseDTO> getMyQueue(String testerEmail) {
        User tester = requireTester(testerEmail);
        return issueRepository.findByTesterIdAndStatusInOrderByUpdatedAtAsc(
                tester.getId(), List.of(IssueStatus.READY_FOR_TESTING.name(), IssueStatus.TESTING.name()))
            .stream().map(this::toIssueResponse).toList();
    }

    @Transactional
    public TestExecutionResponseDTO startTesting(Long issueId, String testerEmail) {
        User tester = requireAuthorizedActor(testerEmail);
        Issue issue = getIssueForUpdate(issueId);
        requireAssignedTesterOrAdmin(issue, tester);
        if (!IssueStatus.READY_FOR_TESTING.name().equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Issue is not ready for testing");
        }
        if (testExecutionRepository.existsByIssueIdAndStatus(issueId, "IN_PROGRESS")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "An active test execution already exists for this issue");
        }

        LocalDateTime now = LocalDateTime.now();
        TestExecution execution = new TestExecution();
        execution.setIssue(issue);
        execution.setTester(tester);
        execution.setStatus("IN_PROGRESS");
        execution.setStartedAt(now);
        execution.setCreatedAt(now);
        TestExecution savedExecution = testExecutionRepository.save(execution);

        issue.setStatus(IssueStatus.TESTING.name());
        issueRepository.save(issue);
        auditLogService.createLog("TEST_STARTED", "Testing started for issue '" + issue.getTitle() + "'", issueId, tester.getId());
        notifyDeveloper(issue, tester, "TEST_STARTED", "Testing started for issue '" + issue.getTitle() + "'");
        return toExecutionResponse(savedExecution);
    }

    @Transactional
    public TestExecutionResponseDTO completeTesting(Long issueId, TestCompletionRequestDTO request, String testerEmail) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test result is required");
        }
        User tester = requireAuthorizedActor(testerEmail);
        Issue issue = getIssueForUpdate(issueId);
        requireAssignedTesterOrAdmin(issue, tester);
        if (!IssueStatus.TESTING.name().equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Issue is not being tested");
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

        issue.setStatus("PASSED".equals(result) ? IssueStatus.RESOLVED.name() : IssueStatus.IN_PROGRESS.name());
        issueRepository.save(issue);

        String action = "PASSED".equals(result) ? "TEST_PASSED" : "TEST_FAILED";
        String details = buildOutcomeDetails(issue, result, execution);
        auditLogService.createLog(action, details, issueId, tester.getId());
        notifyDeveloper(issue, tester, action, details);
        return toExecutionResponse(savedExecution);
    }

    public List<TestExecutionResponseDTO> getHistory(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found");
        }
        return testExecutionRepository.findByIssueIdOrderByCreatedAtDesc(issueId)
            .stream().map(this::toExecutionResponse).toList();
    }

    private User requireTester(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled() || !"TESTER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only TESTER users can perform this action");
        }
        return user;
    }

    private User requireAuthorizedActor(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled() || !("TESTER".equals(user.getRole()) || "ADMIN".equals(user.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only TESTER or ADMIN users can perform this action");
        }
        return user;
    }

    private Issue getIssueForUpdate(Long issueId) {
        return issueRepository.findByIdForUpdate(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    private void requireAssignedTester(Issue issue, User tester) {
        if (issue.getTester() == null || !Objects.equals(issue.getTester().getId(), tester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Issue is assigned to another tester");
        }
    }

    private void requireAssignedTesterOrAdmin(Issue issue, User actor) {
        if ("ADMIN".equals(actor.getRole())) return;
        requireAssignedTester(issue, actor);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildOutcomeDetails(Issue issue, String result, TestExecution execution) {
        return "Issue '" + issue.getTitle() + "' " + result + " testing"
            + appendDetail("Scenario", execution.getTestScenario())
            + appendDetail("Expected", execution.getExpectedResult())
            + appendDetail("Actual", execution.getActualResult())
            + appendDetail("Notes", execution.getNotes());
    }

    private String appendDetail(String label, String value) {
        return value == null || value.isBlank() ? "" : "; " + label + ": " + value;
    }

    private void notifyDeveloper(Issue issue, User tester, String type, String message) {
        User developer = issue.getAssignedUser();
        if (developer == null || Objects.equals(developer.getId(), tester.getId())) return;
        try {
            notificationService.createNotification(developer, type, message);
        } catch (RuntimeException exception) {
            logger.warn("Could not create {} notification for issue {}", type, issue.getId(), exception);
        }
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

    private UserResponseDTO toUserResponse(User user) {
        if (user == null) return null;
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getAvatarUrl());
    }
}
