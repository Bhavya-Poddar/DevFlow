package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.ProjectReportDTO;
import com.devflow.devflow.service.ProjectReportService;

@RestController
@RequestMapping("/api/reports")
public class ProjectReportController {

    private final ProjectReportService projectReportService;

    public ProjectReportController(ProjectReportService projectReportService) {
        this.projectReportService = projectReportService;
    }

    @GetMapping("/projects/{projectId}")
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
