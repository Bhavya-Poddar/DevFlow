package com.devflow.devflow.dto;

import java.util.List;

public record UserMembershipsResponseDTO(List<ProjectMembership> projects, List<TeamMembership> teams) {
    public record ProjectMembership(Long projectId, String projectName, Long roleId, String roleName) {}
    public record TeamMembership(Long teamId, String teamName, Long roleId, String roleName, boolean manager) {}
}
