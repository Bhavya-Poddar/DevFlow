package com.devflow.devflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.IssueReportRowDTO;
import com.devflow.devflow.dto.ProjectReportDTO;
import com.devflow.devflow.dto.ProjectReportSummaryDTO;
import com.devflow.devflow.dto.CollaboratorResponseDTO;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.TestExecution;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.TestExecutionRepository;

@Service
public class ProjectReportService {

    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final TestExecutionRepository testExecutionRepository;

    public ProjectReportService(ProjectRepository projectRepository, IssueRepository issueRepository,
            TestExecutionRepository testExecutionRepository) {
        this.projectRepository = projectRepository;
        this.issueRepository = issueRepository;
        this.testExecutionRepository = testExecutionRepository;
    }

    public ProjectReportDTO getProjectReport(Long projectId, Long developerId, Long testerId,
            Long sprintId, String status, String testResult) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String normalizedStatus = normalizeIssueStatusFilter(status);
        String normalizedResult = normalizeFilter(testResult);
        List<IssueReportRowDTO> rows = new ArrayList<>();
        for (Issue issue : issueRepository.findByProjectId(projectId)) {
            if (developerId != null && !hasId(issue.getAssignedUser(), developerId)) continue;
            if (testerId != null && !hasId(issue.getTester(), testerId)) continue;
            if (sprintId != null && (issue.getSprint() == null || !sprintId.equals(issue.getSprint().getId()))) continue;
            if (normalizedStatus != null && !normalizedStatus.equals(normalizeFilter(issue.getStatus()))) continue;

            List<TestExecution> history = testExecutionRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId());
            TestExecution latest = history.isEmpty() ? null : history.get(0);
            if (normalizedResult != null && (latest == null
                    || !normalizedResult.equals(normalizeFilter(latest.getResult())))) continue;
            rows.add(toRow(issue, latest));
        }

        // CLOSED is retained only as a historical reporting value; it is not an accepted issue status.
        long completed = rows.stream().filter(row -> IssueStatus.RESOLVED.name().equals(row.getStatus())
            || "CLOSED".equals(row.getStatus())).count();
        long testing = rows.stream().filter(row -> IssueStatus.TESTING.name().equals(row.getStatus())).count();
        long passed = rows.stream().filter(row -> "PASSED".equals(row.getTestResult())).count();
        long failed = rows.stream().filter(row -> "FAILED".equals(row.getTestResult())).count();
        long unassigned = rows.stream().filter(row -> row.getDeveloper() == null).count();
        ProjectReportSummaryDTO summary = new ProjectReportSummaryDTO(
            rows.size(), completed, testing, passed, failed, unassigned);
        return new ProjectReportDTO(project.getId(), project.getName(), project.getDescription(), List.copyOf(rows), summary);
    }

    private IssueReportRowDTO toRow(Issue issue, TestExecution latest) {
        return new IssueReportRowDTO(
            issue.getId(), issue.getTitle(), issue.getPriority(), issue.getStatus(),
            toUserResponse(issue.getAssignedUser()), toUserResponse(issue.getTester()),
            issue.getCreatedAt(), issue.getCreatedAt(), issue.getUpdatedAt(),
            latest == null ? null : latest.getStatus(),
            latest == null ? null : latest.getResult(),
            latest == null ? null : latest.getStartedAt(),
            latest == null ? null : latest.getCompletedAt(),
            latest == null ? null : latest.getNotes());
    }

    private boolean hasId(User user, Long id) {
        return user != null && id.equals(user.getId());
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeIssueStatusFilter(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return IssueStatus.fromRequest(value).name();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private CollaboratorResponseDTO toUserResponse(User user) {
        if (user == null) return null;
        return new CollaboratorResponseDTO(user);
    }
}
