package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.DashboardSummaryDTO;
import com.devflow.devflow.dto.DashboardOverviewDTO;
import com.devflow.devflow.dto.DashboardProjectProgressDTO;
import com.devflow.devflow.dto.DashboardWorkloadDTO;
import com.devflow.devflow.dto.DashboardTestingDTO;
import com.devflow.devflow.dto.DashboardBottlenecksDTO;
import com.devflow.devflow.dto.DashboardSprintsDTO;
import com.devflow.devflow.dto.DashboardWorkloadRiskDTO;
import com.devflow.devflow.service.DashboardAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Database-backed engineering work overview.")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final DashboardAnalyticsService dashboardAnalyticsService;

    public DashboardController(DashboardAnalyticsService dashboardAnalyticsService) {
        this.dashboardAnalyticsService = dashboardAnalyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard metrics", description = "Returns current aggregate project, task, and team metrics.")
    public ResponseEntity<DashboardSummaryDTO> getSummary() {
        return ResponseEntity.ok(dashboardAnalyticsService.getSummary());
    }

    @GetMapping("/overview")
    @Operation(summary = "Get filtered dashboard KPIs", description = "Returns task totals and supported status counts. Overdue means unresolved work in a sprint whose end date is before today.")
    public ResponseEntity<DashboardOverviewDTO> getOverview(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getOverview(projectId, sprintId));
    }

    @GetMapping("/project-progress")
    @Operation(summary = "Get project progress", description = "Returns resolved task counts by project, filtered by optional project and sprint IDs.")
    public ResponseEntity<DashboardProjectProgressDTO> getProjectProgress(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getProjectProgress(projectId, sprintId));
    }

    @GetMapping("/workload")
    @Operation(summary = "Get assignment workload", description = "Returns aggregated task assignments for developers and testers within the selected scope.")
    public ResponseEntity<DashboardWorkloadDTO> getWorkload(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getWorkload(projectId, sprintId));
    }

    @GetMapping("/testing")
    @Operation(summary = "Get testing outcomes", description = "Aggregates completed test executions with PASSED or FAILED outcomes; active executions are excluded from the denominator.")
    public ResponseEntity<DashboardTestingDTO> getTestingInsights(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getTestingInsights(projectId, sprintId));
    }

    @GetMapping("/bottlenecks")
    @Operation(summary = "Get delivery bottlenecks", description = "Returns tasks with unresolved blockers and unresolved work in sprints past their end dates.")
    public ResponseEntity<DashboardBottlenecksDTO> getBottlenecks(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getBottlenecks(projectId, sprintId));
    }

    @GetMapping("/sprints")
    @Operation(summary = "Get active sprint progress", description = "Returns live task counts and completion for active sprints. Recorded burndown history remains available from the existing sprint report endpoint.")
    public ResponseEntity<DashboardSprintsDTO> getActiveSprints(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getActiveSprints(projectId, sprintId));
    }

    @GetMapping("/workload-risk")
    @Operation(summary = "Get sprint workload and delivery risks",
        description = "Returns unresolved workload by assignee, overdue and approaching deadlines, and active sprint risk indicators for the selected project/sprint scope.")
    public ResponseEntity<DashboardWorkloadRiskDTO> getWorkloadRisk(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sprintId) {
        return ResponseEntity.ok(dashboardAnalyticsService.getWorkloadRisk(projectId, sprintId));
    }
}
