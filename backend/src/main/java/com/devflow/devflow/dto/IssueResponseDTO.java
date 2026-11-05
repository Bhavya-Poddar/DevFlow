package com.devflow.devflow.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.User;

/** Safe response for issue updates; relationship users are returned without credential fields. */
public class IssueResponseDTO {
    private final Long id;
    private final String title;
    private final String description;
    private final String acceptanceCriteria;
    private final String priority;
    private final String status;
    private final LocalDate dueDate;
    private final ProjectSummaryResponseDTO project;
    private final CollaboratorResponseDTO assignedUser;
    private final CollaboratorResponseDTO tester;
    private final CollaboratorResponseDTO reportedBy;
    private final SprintSummaryResponseDTO sprint;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final boolean blocked;
    private final List<IssueDependencyResponseDTO> blockedBy;
    private final List<IssueDependencyResponseDTO> blocks;

    public IssueResponseDTO(Issue issue) {
        this(issue, List.of(), List.of());
    }

    public IssueResponseDTO(Issue issue, List<IssueDependencyResponseDTO> blockedBy,
            List<IssueDependencyResponseDTO> blocks) {
        this.id = issue.getId();
        this.title = issue.getTitle();
        this.description = issue.getDescription();
        this.acceptanceCriteria = issue.getAcceptanceCriteria();
        this.priority = issue.getPriority();
        this.status = issue.getStatus();
        this.dueDate = issue.getDueDate();
        this.project = issue.getProject() == null ? null : new ProjectSummaryResponseDTO(issue.getProject());
        this.assignedUser = toUserResponse(issue.getAssignedUser());
        this.tester = toUserResponse(issue.getTester());
        this.reportedBy = toUserResponse(issue.getReportedBy());
        this.sprint = issue.getSprint() == null ? null : new SprintSummaryResponseDTO(issue.getSprint());
        this.createdAt = issue.getCreatedAt();
        this.updatedAt = issue.getUpdatedAt();
        this.blockedBy = List.copyOf(blockedBy);
        this.blocks = List.copyOf(blocks);
        this.blocked = blockedBy.stream().anyMatch(dependency ->
            !"RESOLVED".equals(dependency.getStatus()));
    }

    private CollaboratorResponseDTO toUserResponse(User user) {
        return user == null ? null : new CollaboratorResponseDTO(user);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public ProjectSummaryResponseDTO getProject() { return project; }
    public CollaboratorResponseDTO getAssignedUser() { return assignedUser; }
    public CollaboratorResponseDTO getTester() { return tester; }
    public CollaboratorResponseDTO getReportedBy() { return reportedBy; }
    public SprintSummaryResponseDTO getSprint() { return sprint; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean isBlocked() { return blocked; }
    public List<IssueDependencyResponseDTO> getBlockedBy() { return blockedBy; }
    public List<IssueDependencyResponseDTO> getBlocks() { return blocks; }
}
