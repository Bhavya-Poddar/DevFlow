package com.devflow.devflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.PendingApprovalsResponseDTO;
import com.devflow.devflow.service.IssueApprovalService;

/** Returns only approval items the authenticated actor is currently allowed to decide. */
@RestController
@RequestMapping("/api/approvals")
public class PendingApprovalsController {
    private final IssueApprovalService approvals;

    public PendingApprovalsController(IssueApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/pending")
    public ResponseEntity<PendingApprovalsResponseDTO> pending(Authentication authentication) {
        return ResponseEntity.ok(approvals.pendingFor(authentication.getName()));
    }
}
