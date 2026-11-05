package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.service.ProjectReportService;

@ExtendWith(MockitoExtension.class)
class ProjectReportServiceTests {

    @Mock private ProjectRepository projectRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private TestExecutionRepository executionRepository;
    @InjectMocks private ProjectReportService service;

    @Test
    void reportContainsIssuePeopleLifecycleAndTestingHistory() {
        Project project = new Project();
        project.setId(3L);
        project.setName("DevFlow");
        project.setDescription("Engineering platform");
        Issue issue = new Issue();
        issue.setId(8L);
        issue.setTitle("Login bug");
        issue.setPriority("HIGH");
        issue.setStatus("IN_PROGRESS");
        issue.setAssignedUser(user(1L, "dev@example.com", "DEVELOPER"));
        issue.setTester(user(2L, "tester@example.com", "TESTER"));
        issue.setCreatedAt(LocalDateTime.parse("2026-10-02T10:00:00"));
        issue.setUpdatedAt(LocalDateTime.parse("2026-10-05T12:00:00"));
        TestExecution execution = new TestExecution();
        execution.setId(15L);
        execution.setIssue(issue);
        execution.setTester(issue.getTester());
        execution.setStatus("COMPLETED");
        execution.setResult("FAILED");
        execution.setNotes("Special characters fail");
        execution.setStartedAt(LocalDateTime.parse("2026-10-05T11:00:00"));
        execution.setCompletedAt(LocalDateTime.parse("2026-10-05T11:30:00"));
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));
        when(issueRepository.findByProjectId(3L)).thenReturn(List.of(issue));
        when(executionRepository.findByIssueIdOrderByCreatedAtDesc(8L)).thenReturn(List.of(execution));

        var report = service.getProjectReport(3L, null, 2L, null, null, "failed");

        assertEquals("DevFlow", report.getProjectName());
        assertEquals(1, report.getIssues().size());
        assertEquals("dev@example.com", report.getIssues().get(0).getDeveloper().name());
        assertEquals("DEVELOPER", report.getIssues().get(0).getDeveloper().role());
        assertEquals("tester@example.com", report.getIssues().get(0).getTester().name());
        assertEquals("TESTER", report.getIssues().get(0).getTester().role());
        assertEquals("FAILED", report.getIssues().get(0).getTestResult());
        assertEquals("Special characters fail", report.getIssues().get(0).getTestNotes());
        assertEquals(1, report.getSummary().getTotalIssues());
        assertEquals(1, report.getSummary().getFailedTests());
    }

    @Test
    void unknownProjectReturnsNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.getProjectReport(99L, null, null, null, null, null));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void reportStatusFilterAcceptsOnlyApprovedIssueStatuses() {
        Project project = new Project();
        project.setId(3L);
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.getProjectReport(3L, null, null, null, "CLOSED", null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
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
