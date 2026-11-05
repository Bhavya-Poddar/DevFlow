package com.devflow.devflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.AutomationExecutionResponseDTO;
import com.devflow.devflow.dto.AutomationRuleEnabledRequestDTO;
import com.devflow.devflow.dto.AutomationRuleRequestDTO;
import com.devflow.devflow.dto.AutomationRuleResponseDTO;
import com.devflow.devflow.service.AutomationRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/automations")
@Tag(name = "Workflow automations", description = "Manage allow-listed task workflow rules and view their execution history.")
@SecurityRequirement(name = "bearerAuth")
public class AutomationController {
    private final AutomationRuleService automationRuleService;

    public AutomationController(AutomationRuleService automationRuleService) {
        this.automationRuleService = automationRuleService;
    }

    @GetMapping("/rules")
    @Operation(summary = "List automation rules")
    public ResponseEntity<List<AutomationRuleResponseDTO>> listRules(Authentication authentication) {
        return ResponseEntity.ok(automationRuleService.listRules(authentication.getName()));
    }

    @PostMapping("/rules")
    @Operation(summary = "Create an automation rule")
    public ResponseEntity<AutomationRuleResponseDTO> createRule(
            @RequestBody AutomationRuleRequestDTO request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(automationRuleService.createRule(request, authentication.getName()));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update an automation rule")
    public ResponseEntity<AutomationRuleResponseDTO> updateRule(@PathVariable Long id,
            @RequestBody AutomationRuleRequestDTO request, Authentication authentication) {
        return ResponseEntity.ok(automationRuleService.updateRule(id, request, authentication.getName()));
    }

    @PatchMapping("/rules/{id}/enabled")
    @Operation(summary = "Enable or disable an automation rule")
    public ResponseEntity<AutomationRuleResponseDTO> setEnabled(@PathVariable Long id,
            @RequestBody AutomationRuleEnabledRequestDTO request, Authentication authentication) {
        if (request == null || request.getEnabled() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(automationRuleService.setEnabled(id, request.getEnabled(), authentication.getName()));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete an automation rule")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id, Authentication authentication) {
        automationRuleService.deleteRule(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/executions")
    @Operation(summary = "List recent automation executions")
    public ResponseEntity<List<AutomationExecutionResponseDTO>> recentExecutions(Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(automationRuleService.recentExecutions(authentication.getName(), limit));
    }
}
