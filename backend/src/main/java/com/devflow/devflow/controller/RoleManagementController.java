package com.devflow.devflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.RoleResponseDTO;
import com.devflow.devflow.dto.RoleUpsertRequestDTO;
import com.devflow.devflow.service.PermissionCatalogue;
import com.devflow.devflow.service.RoleManagementService;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleManagementController {
    private final RoleManagementService service;

    public RoleManagementController(RoleManagementService service) { this.service = service; }

    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionCatalogue.Entry>> permissions() {
        return ResponseEntity.ok(PermissionCatalogue.editableEntries());
    }

    @GetMapping
    public ResponseEntity<List<RoleResponseDTO>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<RoleResponseDTO> create(@RequestBody RoleUpsertRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, authentication.getName()));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<RoleResponseDTO> update(@PathVariable Long roleId,
            @RequestBody RoleUpsertRequestDTO request, Authentication authentication) {
        return ResponseEntity.ok(service.update(roleId, request, authentication.getName()));
    }

    /** Retirement is reversible in data terms: existing assignments retain their grants, but new assignment is blocked. */
    @DeleteMapping("/{roleId}")
    public ResponseEntity<RoleResponseDTO> retire(@PathVariable Long roleId, Authentication authentication) {
        return ResponseEntity.ok(service.retire(roleId, authentication.getName()));
    }
}
