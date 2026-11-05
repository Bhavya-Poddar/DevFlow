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
import com.devflow.devflow.dto.IssueDependencyCreateRequestDTO;
import com.devflow.devflow.dto.TesterAssignmentRequestDTO;
import com.devflow.devflow.service.IssueService;
import com.devflow.devflow.service.TaskActivityTimelineService;
import com.devflow.devflow.service.ProjectAccessService;
import com.devflow.devflow.dto.TaskActivityTimelineResponseDTO;
import com.devflow.devflow.dto.IssueApprovalRequestDTO;
import com.devflow.devflow.dto.IssueApprovalResponseDTO;
import com.devflow.devflow.dto.MaintenanceFollowUpRequestDTO;
import com.devflow.devflow.dto.IssueMaintenanceResponseDTO;
import com.devflow.devflow.service.IssueApprovalService;
import com.devflow.devflow.service.IssueMaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/issues")
@Tag(name = "Tasks", description = "Create, read, update, assign, and manage task dependencies.")
@SecurityRequirement(name = "bearerAuth")
public class IssueController {
    private final IssueService issueService;
    private final TaskActivityTimelineService taskActivityTimelineService;
    private final ProjectAccessService projectAccessService;
    private final IssueApprovalService approvalService;
    private final IssueMaintenanceService maintenanceService;

    public IssueController(IssueService issueService, TaskActivityTimelineService taskActivityTimelineService,
            ProjectAccessService projectAccessService,
            IssueApprovalService approvalService, IssueMaintenanceService maintenanceService) {
        this.issueService = issueService;
        this.taskActivityTimelineService = taskActivityTimelineService;
        this.projectAccessService = projectAccessService;
        this.approvalService = approvalService;
        this.maintenanceService = maintenanceService;
    }

    @PostMapping
    @Operation(summary = "Create a task", description = "Creates a task using the supplied project and optional relationship IDs. The authenticated user is recorded as the reporter.")
    @ApiResponse(responseCode = "201", description = "Task created; returns the safe task response.")
    public ResponseEntity<IssueResponseDTO> createIssue(@RequestBody IssueCreateRequestDTO issue, Authentication authentication) {
        if (issue.getProject() != null && issue.getProject().getId() != null) {
            projectAccessService.requireProjectAccess(issue.getProject().getId(), authentication.getName());
        }
        Issue createdIssue = issueService.createIssue(issue, authentication.getName());
        return new ResponseEntity<>(new IssueResponseDTO(createdIssue), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List tasks", description = "Returns task response DTOs, including developer, tester, sprint, project, and blocker information.")
    public ResponseEntity<List<IssueResponseDTO>> getAllIssues(Authentication authentication) {
        return ResponseEntity.ok(issueService.getAllIssueResponses(authentication.getName()));
    }

    @GetMapping("/{id}/activity")
    @Operation(summary = "Get task activity", description = "Returns the task's audit/activity history in chronological order.")
    public ResponseEntity<List<IssueActivityResponseDTO>> getIssueActivity(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.getIssueActivity(id));
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get the task activity timeline", description = "Returns the task's persisted DevFlow audit events in chronological order.")
    public ResponseEntity<List<TaskActivityTimelineResponseDTO>> getTaskTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(taskActivityTimelineService.getTimeline(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task", description = "Returns one task by database ID, or 404 when it does not exist.")
    public ResponseEntity<IssueResponseDTO> getIssueById(@PathVariable Long id, Authentication authentication) {
        Optional<IssueResponseDTO> issue = issueService.getIssueResponse(id, authentication.getName());
        return issue.map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task", description = "Updates permitted task fields while preserving omitted fields and enforcing role, ownership, and workflow rules.")
    public ResponseEntity<IssueResponseDTO> updateIssue(
            @PathVariable Long id,
            @RequestBody IssueUpdateRequestDTO updatedIssue,
            Authentication authentication) {
        if (updatedIssue.hasProject()) {
            Long newProjectId = updatedIssue.getProject() == null ? null : updatedIssue.getProject().getId();
            projectAccessService.requireIssueProjectChange(id, newProjectId, authentication.getName());
        }
        Issue issue = issueService.updateIssue(id, updatedIssue, authentication.getName());
        return ResponseEntity.ok(issueService.toIssueResponse(issue, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task", description = "Deletes a task when the caller has the required role and no protected relationship prevents deletion.")
    @ApiResponse(responseCode = "204", description = "Task deleted.")
    public ResponseEntity<Void> deleteIssue(@PathVariable Long id, Authentication authentication) {
        issueService.deleteIssue(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{issueId}/tester")
    @Operation(summary = "Assign a tester", description = "Assigns an existing user with the TESTER role to the task.")
    public ResponseEntity<IssueResponseDTO> assignTester(
            @PathVariable Long issueId,
            @RequestBody TesterAssignmentRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(issueService.toIssueResponse(issueService.assignTester(
            issueId, request.getRequestedTesterId(), authentication.getName()), authentication.getName()));
    }

    @PostMapping("/{issueId}/dependencies")
    @Operation(summary = "Add a task blocker", description = "Links an existing task as a blocker. Duplicate, self, and cyclic links are rejected.")
    public ResponseEntity<IssueResponseDTO> addDependency(
            @PathVariable Long issueId,
            @RequestBody IssueDependencyCreateRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(issueService.addDependency(issueId, request, authentication.getName()));
    }

    @DeleteMapping("/{issueId}/dependencies/{blockingIssueId}")
    @Operation(summary = "Remove a task blocker", description = "Removes the relationship from the blocking task to the affected task.")
    @ApiResponse(responseCode = "204", description = "Dependency removed.")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long issueId,
            @PathVariable Long blockingIssueId,
            Authentication authentication) {
        issueService.removeDependency(issueId, blockingIssueId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/approvals")
    @Operation(summary = "Read task approval history", description = "Returns approval decisions by workflow cycle.")
    public ResponseEntity<List<IssueApprovalResponseDTO>> approvalHistory(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(approvalService.history(id, authentication.getName()));
    }

    @PostMapping("/{id}/approvals/{stage}")
    @Operation(summary = "Record a review decision", description = "The assigned Team Lead or Project Manager may approve or reject. Rejection requires a remark; the first valid decision wins.")
    public ResponseEntity<IssueApprovalResponseDTO> decideApproval(@PathVariable Long id, @PathVariable String stage,
            @RequestBody IssueApprovalRequestDTO request, Authentication authentication) {
        return ResponseEntity.ok(approvalService.decide(id, stage, request, authentication.getName()));
    }

    @GetMapping("/{id}/maintenance")
    @Operation(summary = "Read task maintenance state", description = "Returns a recorded post-resolution maintenance period and linked follow-ups, if one exists.")
    public ResponseEntity<IssueMaintenanceResponseDTO> maintenance(@PathVariable Long id, Authentication authentication) {
        IssueMaintenanceResponseDTO result=maintenanceService.get(id,authentication.getName());
        return result==null?ResponseEntity.noContent().build():ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/maintenance-follow-ups")
    @Operation(summary = "Create a maintenance follow-up", description = "Creates a linked new task during the original task's active maintenance period without reopening the resolved task.")
    public ResponseEntity<IssueMaintenanceResponseDTO.FollowUp> maintenanceFollowUp(@PathVariable Long id,
            @RequestBody MaintenanceFollowUpRequestDTO request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceService.createFollowUp(id,request,authentication.getName()));
    }
}
