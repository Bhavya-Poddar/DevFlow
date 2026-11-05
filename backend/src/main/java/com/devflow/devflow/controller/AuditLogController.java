package com.devflow.devflow.controller;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.dto.AuditLogResponseDTO;
import com.devflow.devflow.service.AuditLogServices;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit history", description = "Read task activity and administrator-only audit cleanup.")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {
    @Autowired
    private AuditLogServices auditLogService;

    @GetMapping
    @Operation(summary = "Search audit history", description = "Returns a newest-first page of audit entries. Optional filters include actorId, action, issueId (the legacy task ID parameter), outcome, from, and to. Outcome is FAILURE for failed login events and SUCCESS for recorded actions.")
    public ResponseEntity<Page<AuditLogResponseDTO>> searchLogs(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long issueId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(auditLogService.searchLogs(actorId, action, issueId, outcome, from, to, page, size));
    }

    @GetMapping("/issue/{issueId}")
    @Operation(summary = "Get audit entries for a task", description = "Returns audit history for a task. ADMIN or PROJECT_MANAGER access is required.")
    public ResponseEntity<List<AuditLog>> getLogsByIssue(@PathVariable Long issueId) {
        return ResponseEntity.ok(auditLogService.getLogsByIssue(issueId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an audit entry", description = "Deletes one audit record. Administrator access is required.")
    public ResponseEntity<String> deleteLog(@PathVariable Long id, Authentication authentication) {
        auditLogService.deleteLog(id, auditLogService.findActorId(authentication.getName()));
        return ResponseEntity.ok("Audit log deleted successfully");
    }
}
