package com.devflow.devflow.controller;

import java.util.List;
import java.util.Optional;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.dto.IssueResponseDTO;
import com.devflow.devflow.dto.IssueCreateRequestDTO;
import com.devflow.devflow.dto.IssueUpdateRequestDTO;
import com.devflow.devflow.dto.IssueActivityResponseDTO;
import com.devflow.devflow.dto.TesterAssignmentRequestDTO;
import com.devflow.devflow.service.IssueService;

@RestController
@RequestMapping("/api/issues")
public class IssueController {
    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public ResponseEntity<IssueResponseDTO> createIssue(@RequestBody IssueCreateRequestDTO issue, Authentication authentication) {
        Issue createdIssue = issueService.createIssue(issue, authentication.getName());
        return new ResponseEntity<>(new IssueResponseDTO(createdIssue), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<IssueResponseDTO>> getAllIssues() {
        List<IssueResponseDTO> issues = issueService.getAllIssues().stream()
            .map(IssueResponseDTO::new)
            .toList();
        return ResponseEntity.ok(issues);
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<List<IssueActivityResponseDTO>> getIssueActivity(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.getIssueActivity(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssueResponseDTO> getIssueById(@PathVariable Long id) {
        Optional<Issue> issue = issueService.getIssueById(id);
        return issue.map(value -> ResponseEntity.ok(new IssueResponseDTO(value)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<IssueResponseDTO> updateIssue(
            @PathVariable Long id,
            @RequestBody IssueUpdateRequestDTO updatedIssue,
            Authentication authentication) {
        Issue issue = issueService.updateIssue(id, updatedIssue, authentication.getName());
        return ResponseEntity.ok(new IssueResponseDTO(issue));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIssue(@PathVariable Long id, Authentication authentication) {
        issueService.deleteIssue(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{issueId}/tester")
    public ResponseEntity<IssueResponseDTO> assignTester(
            @PathVariable Long issueId,
            @RequestBody TesterAssignmentRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(new IssueResponseDTO(issueService.assignTester(
            issueId, request.getRequestedTesterId(), authentication.getName())));
    }
}
