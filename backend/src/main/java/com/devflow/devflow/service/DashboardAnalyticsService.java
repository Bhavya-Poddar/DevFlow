package com.devflow.devflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.dto.DashboardSummaryDTO;
import com.devflow.devflow.dto.DashboardSummaryDTO.ProjectProgress;
import com.devflow.devflow.dto.DashboardSummaryDTO.StatusMetric;
import com.devflow.devflow.dto.DashboardSummaryDTO.TeamWorkload;
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

    public DashboardAnalyticsService(IssueRepository issueRepository, ProjectRepository projectRepository,
            SprintRepository sprintRepository, UserRepository userRepository) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
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

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private TeamWorkload workload(Object[] row, String role) {
        Long userId = (Long) row[0];
        String avatarUrl = row[3] == null ? null : "/api/users/" + userId + "/avatar";
        return new TeamWorkload(userId, (String) row[1], role, number(row[2]), avatarUrl);
    }
}
