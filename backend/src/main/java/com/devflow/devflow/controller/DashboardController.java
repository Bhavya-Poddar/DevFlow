package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.DashboardSummaryDTO;
import com.devflow.devflow.service.DashboardAnalyticsService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardAnalyticsService dashboardAnalyticsService;

    public DashboardController(DashboardAnalyticsService dashboardAnalyticsService) {
        this.dashboardAnalyticsService = dashboardAnalyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getSummary() {
        return ResponseEntity.ok(dashboardAnalyticsService.getSummary());
    }
}
