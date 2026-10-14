package com.devflow.devflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.IssueWorkflowResponseDTO;
import com.devflow.devflow.dto.TestCompletionRequestDTO;
import com.devflow.devflow.dto.TestExecutionResponseDTO;
import com.devflow.devflow.service.TestExecutionService;

@RestController
@RequestMapping("/api/testing")
public class TestingController {

    private final TestExecutionService testExecutionService;

    public TestingController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<IssueWorkflowResponseDTO>> getQueue() {
        return ResponseEntity.ok(testExecutionService.getQueue());
    }

    @GetMapping("/my")
    public ResponseEntity<List<IssueWorkflowResponseDTO>> getMyQueue(Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.getMyQueue(authentication.getName()));
    }

    @PostMapping("/{issueId}/start")
    public ResponseEntity<TestExecutionResponseDTO> startTesting(
            @PathVariable Long issueId, Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.startTesting(issueId, authentication.getName()));
    }

    @PostMapping("/{issueId}/complete")
    public ResponseEntity<TestExecutionResponseDTO> completeTesting(
            @PathVariable Long issueId,
            @RequestBody TestCompletionRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.completeTesting(issueId, request, authentication.getName()));
    }

    @GetMapping("/issue/{issueId}/history")
    public ResponseEntity<List<TestExecutionResponseDTO>> getHistory(@PathVariable Long issueId) {
        return ResponseEntity.ok(testExecutionService.getHistory(issueId));
    }
}
