package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.RoleResponseDTO;
import com.devflow.devflow.dto.ScopedRoleAssignmentRequestDTO;
import com.devflow.devflow.service.RoleManagementService;

@RestController
public class ScopedRoleAssignmentController {
    private final RoleManagementService service;
    public ScopedRoleAssignmentController(RoleManagementService service) { this.service = service; }

    @PutMapping("/api/projects/{projectId}/members/{userId}/role")
    public ResponseEntity<RoleResponseDTO> assignProjectRole(@PathVariable Long projectId,
            @PathVariable Long userId, @RequestBody ScopedRoleAssignmentRequestDTO request,
            Authentication authentication) {
        if (request == null || request.roleId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "roleId is required");
        }
        return ResponseEntity.ok(service.assignProjectRole(projectId, userId, request.roleId(), authentication.getName()));
    }

    @DeleteMapping("/api/projects/{projectId}/members/{userId}/role")
    public ResponseEntity<Void> removeProjectRole(@PathVariable Long projectId, @PathVariable Long userId,
            Authentication authentication) {
        service.removeProjectRole(projectId, userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/teams/{teamId}/members/{userId}/role")
    public ResponseEntity<RoleResponseDTO> assignTeamRole(@PathVariable Long teamId,
            @PathVariable Long userId, @RequestBody ScopedRoleAssignmentRequestDTO request,
            Authentication authentication) {
        if (request == null || request.roleId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "roleId is required");
        }
        return ResponseEntity.ok(service.assignTeamRole(teamId, userId, request.roleId(), authentication.getName()));
    }

    @DeleteMapping("/api/teams/{teamId}/members/{userId}/role")
    public ResponseEntity<Void> removeTeamRole(@PathVariable Long teamId, @PathVariable Long userId,
            Authentication authentication) {
        service.removeTeamRole(teamId, userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
