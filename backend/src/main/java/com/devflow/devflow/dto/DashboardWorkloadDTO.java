package com.devflow.devflow.dto;

import java.util.List;

public record DashboardWorkloadDTO(List<DashboardSummaryDTO.TeamWorkload> members) {}
