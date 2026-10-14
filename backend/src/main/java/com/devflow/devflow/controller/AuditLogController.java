package com.devflow.devflow.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.service.AuditLogServices;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    @Autowired
    private AuditLogServices auditLogService;

    @GetMapping("/issue/{issueId}")
    public ResponseEntity<List<AuditLog>> getLogsByIssue(@PathVariable Long issueId) {
        return ResponseEntity.ok(auditLogService.getLogsByIssue(issueId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteLog(@PathVariable Long id) {
        auditLogService.deleteLog(id);
        return ResponseEntity.ok("Audit log deleted successfully");
    }
}