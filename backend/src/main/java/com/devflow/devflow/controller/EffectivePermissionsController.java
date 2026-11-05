package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.EffectivePermissionsResponseDTO;
import com.devflow.devflow.service.EffectivePermissionService;

@RestController
@RequestMapping("/api/users/me/permissions")
public class EffectivePermissionsController {
    private final EffectivePermissionService service;
    public EffectivePermissionsController(EffectivePermissionService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<EffectivePermissionsResponseDTO> current(Authentication authentication) {
        return ResponseEntity.ok(service.effective(authentication.getName()));
    }
}
