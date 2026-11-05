package com.devflow.devflow.dto;

public record DashboardTestingDTO(
        long completedExecutions,
        long passedExecutions,
        long failedExecutions,
        int passRate,
        String denominator) {}
