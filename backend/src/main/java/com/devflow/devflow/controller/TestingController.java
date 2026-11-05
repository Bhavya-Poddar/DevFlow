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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/testing")
@Tag(name = "Testing workflow", description = "Tester queue, execution, results, and history.")
@SecurityRequirement(name = "bearerAuth")
public class TestingController {

    private final TestExecutionService testExecutionService;

    public TestingController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @GetMapping("/queue")
    @Operation(summary = "Get testing queue", description = "Returns tasks awaiting testing that the caller's role permits them to view.")
    public ResponseEntity<List<IssueWorkflowResponseDTO>> getQueue(Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.getQueue(authentication.getName()));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my testing queue", description = "Returns testing work assigned to the authenticated tester.")
    public ResponseEntity<List<IssueWorkflowResponseDTO>> getMyQueue(Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.getMyQueue(authentication.getName()));
    }

    @PostMapping("/{issueId}/start")
    @Operation(summary = "Start testing a task", description = "Starts a test execution for a task assigned to the authorized tester.")
    public ResponseEntity<TestExecutionResponseDTO> startTesting(
            @PathVariable Long issueId, Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.startTesting(issueId, authentication.getName()));
    }

    @PostMapping("/{issueId}/complete")
    @Operation(summary = "Complete a task test", description = "Records a PASSED or FAILED result. Failure requires notes; pass resolves the task and failure returns it to development.")
    public ResponseEntity<TestExecutionResponseDTO> completeTesting(
            @PathVariable Long issueId,
            @RequestBody TestCompletionRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.completeTesting(issueId, request, authentication.getName()));
    }

    @GetMapping("/issue/{issueId}/history")
    @Operation(summary = "Get test execution history", description = "Returns recorded test executions for a task.")
    public ResponseEntity<List<TestExecutionResponseDTO>> getHistory(@PathVariable Long issueId,
            Authentication authentication) {
        return ResponseEntity.ok(testExecutionService.getHistory(issueId, authentication.getName()));
    }
}
