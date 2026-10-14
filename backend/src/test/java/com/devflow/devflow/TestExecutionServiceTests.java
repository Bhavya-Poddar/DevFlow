package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.TestCompletionRequestDTO;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.TestExecutionService;

@ExtendWith(MockitoExtension.class)
class TestExecutionServiceTests {

    @Mock private IssueRepository issueRepository;
    @Mock private TestExecutionRepository executionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogServices auditLogService;
    @Mock private NotificationService notificationService;
    @InjectMocks private TestExecutionService service;

    private User tester;
    private User developer;
    private Issue issue;

    @BeforeEach
    void setUp() {
        tester = user(10L, "tester@example.com", "TESTER");
        developer = user(20L, "developer@example.com", "DEVELOPER");
        issue = new Issue();
        issue.setId(4L);
        issue.setTitle("Login bug");
        issue.setStatus("READY_FOR_TESTING");
        issue.setTester(tester);
        issue.setAssignedUser(developer);
        lenient().when(userRepository.findByEmailIgnoreCase(tester.getEmail())).thenReturn(Optional.of(tester));
        lenient().when(issueRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(issue));
        lenient().when(executionRepository.save(any(TestExecution.class))).thenAnswer(call -> {
            TestExecution saved = call.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        lenient().when(issueRepository.save(any(Issue.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void myQueueUsesAuthenticatedTestersIdentityAndIncludesActiveStatuses() {
        when(issueRepository.findByTesterIdAndStatusInOrderByUpdatedAtAsc(
                10L, List.of("READY_FOR_TESTING", "TESTING"))).thenReturn(List.of(issue));

        assertEquals(1, service.getMyQueue(tester.getEmail()).size());
        verify(issueRepository).findByTesterIdAndStatusInOrderByUpdatedAtAsc(
            10L, List.of("READY_FOR_TESTING", "TESTING"));
    }

    @Test
    void onlyAssignedTesterCanStartTesting() {
        when(userRepository.findByEmailIgnoreCase("other@example.com"))
            .thenReturn(Optional.of(user(11L, "other@example.com", "TESTER")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.startTesting(4L, "other@example.com"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(executionRepository, never()).save(any(TestExecution.class));
    }

    @Test
    void assignedTesterStartsExecutionAndMovesIssueToTesting() {
        com.devflow.devflow.dto.TestExecutionResponseDTO result = service.startTesting(4L, tester.getEmail());

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("TESTING", issue.getStatus());
        verify(auditLogService).createLog(eq("TEST_STARTED"), any(), eq(4L), eq(10L));
        verify(notificationService).createNotification(eq(developer), eq("TEST_STARTED"), any());
    }

    @Test
    void passingTestCompletesHistoryAndResolvesIssue() {
        TestExecution active = new TestExecution();
        active.setId(99L);
        active.setIssue(issue);
        active.setTester(tester);
        active.setStatus("IN_PROGRESS");
        when(issueRepository.findByIdForUpdate(4L)).thenAnswer(call -> {
            issue.setStatus("TESTING");
            return Optional.of(issue);
        });
        when(executionRepository.findFirstByIssueIdAndTesterIdAndStatusOrderByCreatedAtDesc(4L, 10L, "IN_PROGRESS"))
            .thenReturn(Optional.of(active));
        when(executionRepository.countByIssueIdAndStatus(4L, "IN_PROGRESS")).thenReturn(1L);
        TestCompletionRequestDTO request = completion("PASSED", "Login screen completed successfully.");

        var result = service.completeTesting(4L, request, tester.getEmail());

        assertEquals("PASSED", result.getResult());
        assertEquals("Login with valid credentials", result.getTestScenario());
        assertEquals("Dashboard appears", result.getExpectedResult());
        assertEquals("Dashboard appeared", result.getActualResult());
        assertEquals("Login screen completed successfully.", result.getNotes());
        assertEquals("Dashboard appeared", active.getActualResult());
        assertEquals("COMPLETED", active.getStatus());
        assertEquals("RESOLVED", issue.getStatus());
        verify(auditLogService).createLog(eq("TEST_PASSED"),
            org.mockito.ArgumentMatchers.contains("Notes: Login screen completed successfully."), eq(4L), eq(10L));
        verify(notificationService).createNotification(eq(developer), eq("TEST_PASSED"), any());
    }

    @Test
    void failedTestRequiresNotesAndReturnsIssueToInProgress() {
        TestExecution active = new TestExecution();
        active.setIssue(issue);
        active.setTester(tester);
        active.setStatus("IN_PROGRESS");
        when(issueRepository.findByIdForUpdate(4L)).thenAnswer(call -> {
            issue.setStatus("TESTING");
            return Optional.of(issue);
        });
        when(executionRepository.findFirstByIssueIdAndTesterIdAndStatusOrderByCreatedAtDesc(4L, 10L, "IN_PROGRESS"))
            .thenReturn(Optional.of(active));
        when(executionRepository.countByIssueIdAndStatus(4L, "IN_PROGRESS")).thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.completeTesting(4L, completion("FAILED", "  "), tester.getEmail()));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(executionRepository, never()).save(any(TestExecution.class));

        service.completeTesting(4L, completion("FAILED", "Login fails with special characters."), tester.getEmail());

        assertEquals("IN_PROGRESS", issue.getStatus());
        assertEquals("FAILED", active.getResult());
        verify(auditLogService).createLog(eq("TEST_FAILED"), any(), eq(4L), eq(10L));
        verify(notificationService).createNotification(eq(developer), eq("TEST_FAILED"), any());
    }

    @Test
    void historyIsReturnedInRepositoryOrder() {
        TestExecution newest = execution(2L, "FAILED");
        TestExecution older = execution(1L, "PASSED");
        when(issueRepository.existsById(4L)).thenReturn(true);
        when(executionRepository.findByIssueIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(newest, older));

        var history = service.getHistory(4L);

        assertEquals(List.of(2L, 1L), history.stream().map(item -> item.getId()).toList());
        assertEquals("FAILED", history.get(0).getResult());
    }

    private TestExecution execution(Long id, String result) {
        TestExecution execution = new TestExecution();
        execution.setId(id);
        execution.setIssue(issue);
        execution.setTester(tester);
        execution.setStatus("COMPLETED");
        execution.setResult(result);
        return execution;
    }

    private TestCompletionRequestDTO completion(String result, String notes) {
        TestCompletionRequestDTO request = new TestCompletionRequestDTO();
        request.setResult(result);
        request.setNotes(notes);
        request.setTestScenario("Login with valid credentials");
        request.setExpectedResult("Dashboard appears");
        request.setActualResult("Dashboard appeared");
        return request;
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setName(email);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
