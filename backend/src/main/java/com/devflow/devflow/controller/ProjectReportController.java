package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.ProjectReportDTO;
import com.devflow.devflow.service.ProjectReportService;
import com.devflow.devflow.dto.SprintBurndownResponseDTO;
import com.devflow.devflow.service.SprintBurndownSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Project reporting and recorded sprint burndown analytics.")
@SecurityRequirement(name = "bearerAuth")
public class ProjectReportController {

    private final ProjectReportService projectReportService;
    private final SprintBurndownSnapshotService burndownSnapshotService;

    public ProjectReportController(ProjectReportService projectReportService,
            SprintBurndownSnapshotService burndownSnapshotService) {
        this.projectReportService = projectReportService;
        this.burndownSnapshotService = burndownSnapshotService;
    }

    @GetMapping("/sprints/{sprintId}/burndown")
    @Operation(summary = "Get sprint burndown", description = "Returns actual recorded sprint snapshots and an ideal trend based on the recorded tracking period.")
    public ResponseEntity<SprintBurndownResponseDTO> getSprintBurndown(@PathVariable Long sprintId) {
        return ResponseEntity.ok(burndownSnapshotService.getBurndown(sprintId));
    }

    @GetMapping("/projects/{projectId}")
    @Operation(summary = "Get a project report", description = "Returns project task and testing metrics with optional developer, tester, sprint, status, and test-result filters.")
    public ResponseEntity<ProjectReportDTO> getProjectReport(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long developerId,
            @RequestParam(required = false) Long testerId,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String testResult) {
        return ResponseEntity.ok(projectReportService.getProjectReport(
            projectId, developerId, testerId, sprintId, status, testResult));
    }
}
