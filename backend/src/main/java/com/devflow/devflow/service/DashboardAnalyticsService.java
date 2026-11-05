package com.devflow.devflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.dto.DashboardSummaryDTO;
import com.devflow.devflow.dto.DashboardSummaryDTO.ProjectProgress;
import com.devflow.devflow.dto.DashboardSummaryDTO.StatusMetric;
import com.devflow.devflow.dto.DashboardSummaryDTO.TeamWorkload;
import com.devflow.devflow.dto.DashboardOverviewDTO;
import com.devflow.devflow.dto.DashboardProjectProgressDTO;
import com.devflow.devflow.dto.DashboardWorkloadDTO;
import com.devflow.devflow.dto.DashboardTestingDTO;
import com.devflow.devflow.dto.DashboardBottlenecksDTO;
import com.devflow.devflow.dto.DashboardSprintsDTO;
import com.devflow.devflow.dto.DashboardWorkloadRiskDTO;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class DashboardAnalyticsService {
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final IssueDependencyRepository issueDependencyRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final Clock clock;

    public DashboardAnalyticsService(IssueRepository issueRepository, ProjectRepository projectRepository,
            SprintRepository sprintRepository, UserRepository userRepository,
            IssueDependencyRepository issueDependencyRepository, TestExecutionRepository testExecutionRepository,
            Clock clock) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.issueDependencyRepository = issueDependencyRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDTO getSummary() {
        List<Object[]> statusRows = issueRepository.dashboardStatusCounts();
        List<StatusMetric> statuses = statusRows.stream()
            .map(row -> new StatusMetric((String) row[0], number(row[1])))
            .sorted(Comparator.comparing(StatusMetric::status))
            .toList();
        Map<String, Long> countsByStatus = new HashMap<>();
        statuses.forEach(metric -> countsByStatus.put(metric.status(), metric.count()));

        List<ProjectProgress> projectProgress = issueRepository.dashboardProjectProgress().stream()
            .map(row -> {
                long total = number(row[2]);
                long completed = number(row[3]);
                int percent = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
                return new ProjectProgress((Long) row[0], (String) row[1], total, completed, percent);
            })
            .toList();

        List<TeamWorkload> teamWorkload = new ArrayList<>();
        issueRepository.dashboardDeveloperWorkload().forEach(row ->
            teamWorkload.add(workload(row, "DEVELOPER")));
        issueRepository.dashboardTesterWorkload().forEach(row ->
            teamWorkload.add(workload(row, "TESTER")));
        teamWorkload.sort(Comparator.comparingLong(TeamWorkload::assignedIssues).reversed()
            .thenComparing(TeamWorkload::name, String.CASE_INSENSITIVE_ORDER));

        long totalIssues = statuses.stream().mapToLong(StatusMetric::count).sum();
        long completedIssues = countsByStatus.getOrDefault("RESOLVED", 0L);
        long testingIssues = countsByStatus.getOrDefault("TESTING", 0L)
            + countsByStatus.getOrDefault("READY_FOR_TESTING", 0L);
        return new DashboardSummaryDTO(
            projectRepository.count(), totalIssues, completedIssues, totalIssues - completedIssues,
            testingIssues, sprintRepository.count(), sprintRepository.countByStatusIgnoreCase("ACTIVE"),
            userRepository.countByEnabledTrue(), statuses, projectProgress, teamWorkload);
    }

    @Transactional(readOnly = true)
    public DashboardOverviewDTO getOverview(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        Map<String, Long> counts = statusCounts(projectId, sprintId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long completed = counts.getOrDefault(IssueStatus.RESOLVED.name(), 0L);
        long inProgress = counts.getOrDefault(IssueStatus.IN_PROGRESS.name(), 0L);
        long overdue = issueRepository.countUnresolvedIssuesInEndedSprints(LocalDate.now(clock), projectId, sprintId);
        List<DashboardSummaryDTO.StatusMetric> distribution = java.util.Arrays.stream(IssueStatus.values())
            .map(status -> new DashboardSummaryDTO.StatusMetric(status.name(), counts.getOrDefault(status.name(), 0L)))
            .toList();
        return new DashboardOverviewDTO(total, completed, inProgress, overdue,
            total == 0 ? 0 : (int) Math.round(completed * 100.0 / total),
            "Unresolved tasks assigned to a sprint whose end date is before today", distribution);
    }

    @Transactional(readOnly = true)
    public DashboardProjectProgressDTO getProjectProgress(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        List<DashboardSummaryDTO.ProjectProgress> projects = issueRepository.dashboardProjectProgress(projectId, sprintId)
            .stream().map(row -> {
                long total = number(row[2]);
                long completed = number(row[3]);
                return new ProjectProgress(((Number) row[0]).longValue(), (String) row[1], total, completed,
                    total == 0 ? 0 : (int) Math.round(completed * 100.0 / total));
            }).toList();
        return new DashboardProjectProgressDTO(projects);
    }

    @Transactional(readOnly = true)
    public DashboardWorkloadDTO getWorkload(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        List<TeamWorkload> members = new ArrayList<>();
        issueRepository.dashboardDeveloperWorkload(projectId, sprintId).forEach(row -> members.add(workload(row, "DEVELOPER")));
        issueRepository.dashboardTesterWorkload(projectId, sprintId).forEach(row -> members.add(workload(row, "TESTER")));
        members.sort(Comparator.comparingLong(TeamWorkload::assignedIssues).reversed()
            .thenComparing(TeamWorkload::name, String.CASE_INSENSITIVE_ORDER));
        return new DashboardWorkloadDTO(members);
    }

    @Transactional(readOnly = true)
    public DashboardTestingDTO getTestingInsights(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        long passed = 0;
        long failed = 0;
        for (Object[] row : testExecutionRepository.dashboardCompletedResults(projectId, sprintId)) {
            if ("PASSED".equals(row[0])) passed = number(row[1]);
            if ("FAILED".equals(row[0])) failed = number(row[1]);
        }
        long denominator = passed + failed;
        return new DashboardTestingDTO(denominator, passed, failed,
            denominator == 0 ? 0 : (int) Math.round(passed * 100.0 / denominator),
            "Completed test executions with a PASSED or FAILED result; in-progress executions are excluded");
    }

    @Transactional(readOnly = true)
    public DashboardBottlenecksDTO getBottlenecks(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        List<DashboardBottlenecksDTO.BlockedTask> blocked = issueDependencyRepository
            .findDashboardBlockedTasks(projectId, sprintId).stream().map(row ->
                new DashboardBottlenecksDTO.BlockedTask(numberObject(row[0]), (String) row[1], (String) row[2],
                    numberObject(row[3]), (String) row[4], numberObject(row[5]), (String) row[6],
                    (String) row[7], (String) row[8])).toList();
        List<DashboardBottlenecksDTO.OverdueTask> overdue = issueRepository
            .findUnresolvedIssuesInEndedSprints(LocalDate.now(clock), projectId, sprintId).stream().map(row ->
                new DashboardBottlenecksDTO.OverdueTask(numberObject(row[0]), (String) row[1], (String) row[2],
                    numberObject(row[3]), (String) row[4], numberObject(row[5]), (String) row[6],
                    (LocalDate) row[7], (String) row[8])).toList();
        return new DashboardBottlenecksDTO(blocked, overdue,
            "Unresolved tasks assigned to a sprint whose end date is before today");
    }

    @Transactional(readOnly = true)
    public DashboardSprintsDTO getActiveSprints(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        List<DashboardSprintsDTO.ActiveSprint> active = sprintRepository.dashboardActiveSprints(projectId, sprintId)
            .stream().map(row -> {
                long total = number(row[7]);
                long completed = number(row[8]);
                return new DashboardSprintsDTO.ActiveSprint(numberObject(row[0]), (String) row[1], (String) row[2],
                    numberObject(row[3]), (String) row[4], (LocalDate) row[5], (LocalDate) row[6], total,
                    completed, total == 0 ? 0 : (int) Math.round(completed * 100.0 / total));
            }).toList();
        return new DashboardSprintsDTO(active);
    }

    @Transactional(readOnly = true)
    public DashboardWorkloadRiskDTO getWorkloadRisk(Long projectId, Long sprintId) {
        validateFilters(projectId, sprintId);
        LocalDate today = LocalDate.now(clock);
        LocalDate throughDate = today.plusDays(7);
        PageRequest topTasks = PageRequest.of(0, 10);
        List<DashboardWorkloadRiskDTO.RiskTask> overdue = issueRepository
            .findDashboardOverdueTasks(today, projectId, sprintId, topTasks).stream()
            .map(this::riskTask).toList();
        List<DashboardWorkloadRiskDTO.RiskTask> approaching = issueRepository
            .findDashboardApproachingDeadlineTasks(today, throughDate, projectId, sprintId, topTasks).stream()
            .map(this::riskTask).toList();
        List<DashboardWorkloadRiskDTO.AssigneeLoad> assignees = issueRepository
            .dashboardUnresolvedWorkloadByAssignee(projectId, sprintId).stream().map(row -> {
                Long userId = numberObject(row[0]);
                String avatarUrl = userId != null && row[2] != null ? "/api/users/" + userId + "/avatar" : null;
                return new DashboardWorkloadRiskDTO.AssigneeLoad(userId,
                    row[1] == null ? "Unassigned" : (String) row[1], avatarUrl, number(row[3]));
            }).toList();
        List<DashboardWorkloadRiskDTO.SprintRisk> sprints = sprintRepository
            .dashboardSprintWorkloadRisk(today, throughDate, projectId, sprintId).stream().map(row -> {
                long total = number(row[6]);
                long completed = number(row[7]);
                long unresolved = number(row[8]);
                long overdueCount = number(row[9]);
                long approachingCount = number(row[10]);
                LocalDate endDate = (LocalDate) row[5];
                String risk = unresolved == 0 ? "COMPLETE"
                    : overdueCount > 0 || (endDate != null && endDate.isBefore(today)) ? "OVERDUE"
                    : endDate != null && !endDate.isAfter(throughDate) ? "AT_RISK" : "ON_TRACK";
                return new DashboardWorkloadRiskDTO.SprintRisk(numberObject(row[0]), (String) row[1],
                    (String) row[2], numberObject(row[3]), (String) row[4], endDate, total, completed,
                    unresolved, overdueCount, approachingCount,
                    total == 0 ? 0 : (int) Math.round(completed * 100.0 / total), risk);
            }).toList();
        return new DashboardWorkloadRiskDTO(today, throughDate, overdue, approaching, assignees, sprints,
            "Only unresolved tasks are counted. Overdue means due before today; approaching means due today through "
                + "the next 7 days, inclusive. Sprint risk is COMPLETE with no unresolved tasks, OVERDUE when an "
                + "unresolved sprint task is overdue or the sprint end date has passed, AT_RISK when the sprint ends "
                + "within 7 days, and ON_TRACK otherwise. Task lists show the 10 nearest deadlines in each category.");
    }

    private DashboardWorkloadRiskDTO.RiskTask riskTask(Object[] row) {
        return new DashboardWorkloadRiskDTO.RiskTask(numberObject(row[0]), (String) row[1], (String) row[2],
            (LocalDate) row[3], numberObject(row[4]), (String) row[5], numberObject(row[6]), (String) row[7],
            (String) row[8]);
    }

    private Map<String, Long> statusCounts(Long projectId, Long sprintId) {
        Map<String, Long> counts = new HashMap<>();
        issueRepository.dashboardStatusCounts(projectId, sprintId)
            .forEach(row -> counts.put((String) row[0], number(row[1])));
        return counts;
    }

    private void validateFilters(Long projectId, Long sprintId) {
        if (projectId != null && !projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (sprintId != null) {
            Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
            Project sprintProject = sprint.getProject();
            if (projectId != null && (sprintProject == null || !projectId.equals(sprintProject.getId()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint does not belong to the selected project");
            }
        }
    }

    private Long numberObject(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private TeamWorkload workload(Object[] row, String role) {
        Long userId = (Long) row[0];
        String avatarUrl = row[3] == null ? null : "/api/users/" + userId + "/avatar";
        return new TeamWorkload(userId, (String) row[1], role, number(row[2]), avatarUrl);
    }
}
