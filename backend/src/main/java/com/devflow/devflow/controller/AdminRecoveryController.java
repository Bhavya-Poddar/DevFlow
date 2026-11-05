package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.RecoveryCapabilityRequestDTO;
import com.devflow.devflow.service.RoleManagementService;

@RestController
@RequestMapping("/api/admin/recovery")
public class AdminRecoveryController {
    private final RoleManagementService service;
    public AdminRecoveryController(RoleManagementService service) { this.service = service; }

    @PutMapping("/accounts/{userId}")
    public ResponseEntity<Void> setRecoveryCapability(@PathVariable Long userId,
            @RequestBody RecoveryCapabilityRequestDTO request, Authentication authentication) {
        if (request == null || request.userId() == null || !userId.equals(request.userId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Request userId must match the path");
        }
        service.setRecoveryCapability(userId, request.enabled(), authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
