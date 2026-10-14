package com.devflow.devflow.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.dto.IssueUpdateRequestDTO;
import com.devflow.devflow.dto.IssueCreateRequestDTO;
import com.devflow.devflow.dto.IssueActivityResponseDTO;
import com.devflow.devflow.dto.IdReferenceRequestDTO;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IssueService {
    private static final Logger logger = LoggerFactory.getLogger(IssueService.class);

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final AuditLogServices auditLogService;
    private final NotificationService notificationService;
    private final CommentRepository commentRepository;
    private final TestExecutionRepository testExecutionRepository;

    public IssueService(IssueRepository issueRepository, ProjectRepository projectRepository,
            SprintRepository sprintRepository, UserRepository userRepository,
            AuditLogServices auditLogService, NotificationService notificationService,
            CommentRepository commentRepository, TestExecutionRepository testExecutionRepository) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.commentRepository = commentRepository;
        this.testExecutionRepository = testExecutionRepository;
    }

    @Transactional
    public Issue createIssue(IssueCreateRequestDTO request, String userEmail) {
        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        requireIssueEditor(actor);

        String initialStatus = request.getStatus() == null
            ? IssueStatus.OPEN.name() : validateManualStatusTarget(request.getStatus());

        Issue issue = new Issue();
        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());
        issue.setAcceptanceCriteria(request.getAcceptanceCriteria());
        issue.setPriority(request.getPriority());
        issue.setStatus(initialStatus);
        issue.setProject(resolveProjectReference(request.getProject()));
        issue.setSprint(resolveSprintReference(request.getSprint()));
        validateSprintProject(issue.getProject(), issue.getSprint());

        if ("DEVELOPER".equalsIgnoreCase(actor.getRole())) {
            issue.setAssignedUser(null);
        } else {
            User assignee = resolveUserReference(request.getAssignedUser(), "Developer");
            if (assignee != null) validateDeveloperRole(assignee);
            issue.setAssignedUser(assignee);
        }
        // Tester assignment is handled by the dedicated validated assignment flow.
        issue.setTester(null);
        issue.setReportedBy(actor);

        Issue savedIssue = issueRepository.save(issue);

        if (savedIssue.getAssignedUser() != null) {
            notifyUser(savedIssue.getAssignedUser().getId(), "ISSUE_ASSIGNED",
                "You were assigned issue '" + savedIssue.getTitle() + "'");
        }

        auditLogService.createLog(
            "ISSUE_CREATED",
            "Issue '" + auditValue(savedIssue.getTitle()) + "' was created with status '" + savedIssue.getStatus() + "'",
            savedIssue.getId(),
            actor.getId()
        );
        if (savedIssue.getAssignedUser() != null) {
            auditLogService.createLog("ASSIGNED_USER_CHANGED",
                "Assignee changed from 'Unassigned' to '"
                    + auditReference(savedIssue.getAssignedUser().getName(), savedIssue.getAssignedUser().getId()) + "'",
                savedIssue.getId(), actor.getId());
        }

        return savedIssue;
    }

    public List<Issue> getAllIssues() {
        return issueRepository.findAll();
    }

    public Optional<Issue> getIssueById(Long id) {
        return issueRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<IssueActivityResponseDTO> getIssueActivity(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found");
        }
        return auditLogService.getLogsByIssue(issueId).stream()
            .map(IssueActivityResponseDTO::new)
            .toList();
    }

    @Transactional
    public Issue updateIssue(Long id, IssueUpdateRequestDTO updatedIssue, String userEmail) {
        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        requireIssueEditor(actor, id);

        Issue existingIssue = issueRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        boolean isDeveloper = "DEVELOPER".equals(actor.getRole());
        if (isDeveloper) {
            boolean assignedToDeveloper = existingIssue.getAssignedUser() != null
                && Objects.equals(actor.getId(), existingIssue.getAssignedUser().getId());

            if (!assignedToDeveloper) {
                logIssueUpdateDenied(id, actor, "issue is not assigned to this developer");
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Developers can only edit issues assigned to themselves"
                );
            }

            if ((updatedIssue.hasAssignedUser()
                    && !sameUser(existingIssue.getAssignedUser(), updatedIssue.getAssignedUser()))
                    || (updatedIssue.hasProject()
                    && !sameProject(existingIssue.getProject(), updatedIssue.getProject()))
                    || (updatedIssue.hasSprint()
                    && !sameSprint(existingIssue.getSprint(), updatedIssue.getSprint()))
                    || (updatedIssue.hasTester()
                    && !sameUser(existingIssue.getTester(), updatedIssue.getTester()))) {
                logIssueUpdateDenied(id, actor, "developer attempted a protected relationship change");
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Developers cannot change an issue's assignee, project, or sprint"
                );
            }
        }

        String oldTitle = existingIssue.getTitle();
        String oldDescription = existingIssue.getDescription();
        String oldAcceptanceCriteria = existingIssue.getAcceptanceCriteria();
        String oldPriority = existingIssue.getPriority();
        Long oldProjectId = existingIssue.getProject() == null ? null : existingIssue.getProject().getId();
        String oldProject = existingIssue.getProject() == null
            ? "No Project" : existingIssue.getProject().getName();
        String oldStatus = existingIssue.getStatus();
        String newStatus = updatedIssue.hasStatus()
            ? validateManualStatusTarget(updatedIssue.getStatus()) : oldStatus;
        if (isDeveloper && !Objects.equals(oldStatus, newStatus)) {
            validateDeveloperStatusTransition(id, actor, oldStatus, newStatus);
        } else if (updatedIssue.hasStatus() && !Objects.equals(oldStatus, newStatus)) {
            validateManagerStatusTransition(oldStatus, newStatus);
        }
        Long oldAssignedUserId = existingIssue.getAssignedUser() != null
            ? existingIssue.getAssignedUser().getId() : null;
        String oldAssignedUser = existingIssue.getAssignedUser() != null
            ? existingIssue.getAssignedUser().getName() : "Unassigned";
        String oldSprint = existingIssue.getSprint() != null
            ? existingIssue.getSprint().getName() : "No Sprint";
        Long oldSprintId = existingIssue.getSprint() == null ? null : existingIssue.getSprint().getId();
        Long oldTesterId = existingIssue.getTester() == null ? null : existingIssue.getTester().getId();
        String oldTesterName = existingIssue.getTester() == null
            ? "Unassigned" : existingIssue.getTester().getName();

        User updatedTester = updatedIssue.hasTester()
            ? resolveTesterReference(updatedIssue.getTester()) : existingIssue.getTester();
        if (IssueStatus.TESTING.name().equals(oldStatus) && updatedIssue.hasTester()
                && !Objects.equals(oldTesterId, updatedTester == null ? null : updatedTester.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tester assignment cannot change while testing is in progress");
        }

        Project updatedProject = updatedIssue.hasProject()
            ? resolveProjectReference(updatedIssue.getProject()) : existingIssue.getProject();
        Sprint updatedSprint = updatedIssue.hasSprint()
            ? resolveSprintReference(updatedIssue.getSprint()) : existingIssue.getSprint();
        validateSprintProject(updatedProject, updatedSprint);

        if (updatedIssue.hasTitle()) existingIssue.setTitle(updatedIssue.getTitle());
        if (updatedIssue.hasDescription()) existingIssue.setDescription(updatedIssue.getDescription());
        if (updatedIssue.hasAcceptanceCriteria()) existingIssue.setAcceptanceCriteria(updatedIssue.getAcceptanceCriteria());
        if (updatedIssue.hasPriority()) existingIssue.setPriority(updatedIssue.getPriority());
        if (updatedIssue.hasStatus()) existingIssue.setStatus(newStatus);
        if (!isDeveloper && updatedIssue.hasProject()) {
            existingIssue.setProject(updatedProject);
        }
        if (!isDeveloper && updatedIssue.hasAssignedUser()) {
            User assignedUser = resolveUserReference(updatedIssue.getAssignedUser(), "Developer");
            if (assignedUser != null) validateDeveloperRole(assignedUser);
            existingIssue.setAssignedUser(assignedUser);
        }
        if (!isDeveloper && updatedIssue.hasSprint()) {
            existingIssue.setSprint(updatedSprint);
        }
        if (!isDeveloper && updatedIssue.hasTester()) {
            existingIssue.setTester(updatedTester);
        }

        String newAssignedUser = existingIssue.getAssignedUser() == null
            ? "Unassigned" : existingIssue.getAssignedUser().getName();
        String newSprint = existingIssue.getSprint() == null
            ? "No Sprint" : existingIssue.getSprint().getName();
        Issue savedIssue = issueRepository.save(existingIssue);

        recordFieldChange(savedIssue, actor, "TITLE_CHANGED", "Title", oldTitle, savedIssue.getTitle());
        recordFieldChange(savedIssue, actor, "DESCRIPTION_CHANGED", "Description",
            oldDescription, savedIssue.getDescription());
        recordFieldChange(savedIssue, actor, "ACCEPTANCE_CRITERIA_CHANGED", "Acceptance criteria",
            oldAcceptanceCriteria, savedIssue.getAcceptanceCriteria());
        recordFieldChange(savedIssue, actor, "PRIORITY_CHANGED", "Priority",
            oldPriority, savedIssue.getPriority());
        String newProject = savedIssue.getProject() == null
            ? "No Project" : savedIssue.getProject().getName();
        Long newProjectId = savedIssue.getProject() == null ? null : savedIssue.getProject().getId();
        if (!Objects.equals(oldProjectId, newProjectId)) {
            auditLogService.createLog("PROJECT_CHANGED",
                "Project changed from '" + auditReference(oldProject, oldProjectId)
                    + "' to '" + auditReference(newProject, newProjectId) + "'",
                savedIssue.getId(), actor.getId());
        }

        if (!Objects.equals(oldStatus, savedIssue.getStatus())) {
            auditLogService.createLog(
                "STATUS_CHANGED",
                "Status changed from '" + oldStatus + "' to '" + savedIssue.getStatus() + "'",
                savedIssue.getId(),
                actor.getId()
            );

            if (savedIssue.getAssignedUser() != null) {
                notifyUser(savedIssue.getAssignedUser().getId(), "ISSUE_STATUS_CHANGED",
                    "Issue '" + savedIssue.getTitle() + "' changed status to " + savedIssue.getStatus());
            }

            if (IssueStatus.READY_FOR_TESTING.name().equals(savedIssue.getStatus()) && savedIssue.getTester() != null) {
                notifyUser(savedIssue.getTester().getId(), "READY_FOR_TESTING",
                    "Issue '" + savedIssue.getTitle() + "' is ready for testing");
            }
        }

        Long newTesterId = savedIssue.getTester() == null ? null : savedIssue.getTester().getId();
        if (!Objects.equals(oldTesterId, newTesterId)) {
            String newTesterName = savedIssue.getTester() == null ? "Unassigned" : savedIssue.getTester().getName();
            auditLogService.createLog(
                "TESTER_ASSIGNED",
                "Tester changed from '" + auditReference(oldTesterName, oldTesterId)
                    + "' to '" + auditReference(newTesterName, newTesterId) + "'",
                savedIssue.getId(), actor.getId());
            if (savedIssue.getTester() != null) {
                notifyUser(savedIssue.getTester().getId(), "TESTER_ASSIGNED",
                    "You have been assigned to test issue '" + savedIssue.getTitle() + "'");
            }
        }

        Long newAssignedUserId = savedIssue.getAssignedUser() != null
            ? savedIssue.getAssignedUser().getId() : null;
        if (!Objects.equals(oldAssignedUserId, newAssignedUserId)) {
            auditLogService.createLog(
                "ASSIGNED_USER_CHANGED",
                "Assignee changed from '" + auditReference(oldAssignedUser, oldAssignedUserId)
                    + "' to '" + auditReference(newAssignedUser, newAssignedUserId) + "'",
                savedIssue.getId(),
                actor.getId()
            );
        }

        if (!Objects.equals(oldAssignedUserId, newAssignedUserId) && newAssignedUserId != null) {
            notifyUser(newAssignedUserId, "ISSUE_ASSIGNED",
                "You were assigned issue '" + savedIssue.getTitle() + "'");
        }

        Long newSprintId = savedIssue.getSprint() == null ? null : savedIssue.getSprint().getId();
        if (!Objects.equals(oldSprintId, newSprintId)) {
            auditLogService.createLog(
                "SPRINT_CHANGED",
                "Sprint changed from '" + auditReference(oldSprint, oldSprintId)
                    + "' to '" + auditReference(newSprint, newSprintId) + "'",
                savedIssue.getId(),
                actor.getId()
            );
        }

        return savedIssue;
    }

    @Transactional
    public void deleteIssue(Long id, String userEmail) {
        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!isAdministrator(actor) && !isProjectManager(actor)) {
            logger.warn("Issue deletion denied: issueId={} actorId={} actorRole={} reason={}",
                id, actor.getId(), actor.getRole(), "role must be ADMIN or PROJECT_MANAGER");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only admins and project managers can delete issues");
        }

        Issue issue = issueRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        if (commentRepository.existsByIssueId(id) || testExecutionRepository.existsByIssueId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Issue has comments or testing history and cannot be deleted without removing that history");
        }

        // Audit rows are retained after deletion; only their optional issue link is released.
        auditLogService.detachIssueReference(id);
        issueRepository.deleteById(id);

        auditLogService.createLog(
            "ISSUE_DELETED",
            "Issue '" + issue.getTitle() + "' was deleted",
            null,
            actor.getId()
        );
    }

    private void requireIssueEditor(User actor) {
        if (!isAdministrator(actor) && !isProjectManager(actor) && !isDeveloper(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Your role cannot create or update issues");
        }
    }

    private void requireIssueEditor(User actor, Long issueId) {
        if (!isAdministrator(actor) && !isProjectManager(actor) && !isDeveloper(actor)) {
            logIssueUpdateDenied(issueId, actor, "role cannot update issues");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Your role cannot create or update issues");
        }
    }

    private void logIssueUpdateDenied(Long issueId, User actor, String reason) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.warn("Issue update denied by service: issueId={} actorId={} storedRole={} authorities={} reason={}",
            issueId,
            actor.getId(),
            actor.getRole(),
            authentication == null ? "none" : authentication.getAuthorities(),
            reason);
    }

    private boolean isAdministrator(User user) {
        return "ADMIN".equals(user.getRole());
    }

    private boolean isProjectManager(User user) {
        return "PROJECT_MANAGER".equals(user.getRole());
    }

    private boolean isDeveloper(User user) {
        return "DEVELOPER".equals(user.getRole());
    }

    private boolean sameUser(User current, IdReferenceRequestDTO requested) {
        return Objects.equals(
            current == null ? null : current.getId(),
            requested == null ? null : requested.getId()
        );
    }

    private boolean sameProject(Project current, IdReferenceRequestDTO requested) {
        return Objects.equals(
            current == null ? null : current.getId(),
            requested == null ? null : requested.getId()
        );
    }

    private boolean sameSprint(Sprint current, IdReferenceRequestDTO requested) {
        return Objects.equals(
            current == null ? null : current.getId(),
            requested == null ? null : requested.getId()
        );
    }

    private void recordFieldChange(Issue issue, User actor, String action, String field,
            String oldValue, String newValue) {
        if (Objects.equals(oldValue, newValue)) return;
        auditLogService.createLog(action,
            field + " changed from '" + auditValue(oldValue) + "' to '" + auditValue(newValue) + "'",
            issue.getId(), actor.getId());
    }

    /** AuditLog.details uses the existing short text column; keep previews within its limit. */
    private String auditValue(String value) {
        if (value == null || value.isBlank()) return "(empty)";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 70 ? normalized : normalized.substring(0, 67) + "...";
    }

    private String auditReference(String name, Long id) {
        return id == null ? "Unassigned" : auditValue(name) + " (#" + id + ")";
    }

    private void notifyUser(Long userId, String type, String message) {
        try {
            userRepository.findById(userId)
                .ifPresent(user -> notificationService.createNotification(user, type, message));
        } catch (RuntimeException exception) {
            logger.warn("Could not create {} notification for user {}", type, userId, exception);
        }
    }

    @Transactional
    public Issue assignTester(Long issueId, Long testerId, String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actor not found"));
        if (!"ADMIN".equals(actor.getRole()) && !"PROJECT_MANAGER".equals(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins and project managers can assign testers");
        }

        if (testerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "testerId is required");
        }
        Issue issue = issueRepository.findByIdForUpdate(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        if (IssueStatus.TESTING.name().equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tester assignment cannot change while testing is in progress");
        }
        User tester = userRepository.findById(testerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tester not found"));
        validateTesterRole(tester);

        Long previousTesterId = issue.getTester() == null ? null : issue.getTester().getId();
        String previousTesterName = issue.getTester() == null ? "Unassigned" : issue.getTester().getName();
        issue.setTester(tester);
        Issue savedIssue = issueRepository.save(issue);

        if (!Objects.equals(previousTesterId, tester.getId())) {
            auditLogService.createLog(
                "TESTER_ASSIGNED",
                "Tester changed from '" + auditReference(previousTesterName, previousTesterId)
                    + "' to '" + auditReference(tester.getName(), tester.getId()) + "'",
                issue.getId(),
                actor.getId());
            notifyUser(tester.getId(), "TESTER_ASSIGNED",
                "You have been assigned to test issue '" + issue.getTitle() + "'");
        }
        return savedIssue;
    }

    private User resolveTesterReference(IdReferenceRequestDTO requestedTester) {
        User tester = resolveUserReference(requestedTester, "Tester");
        if (tester != null) validateTesterRole(tester);
        return tester;
    }

    private void validateTesterRole(User tester) {
        if (!"TESTER".equals(tester.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user must have the TESTER role");
        }
    }

    private User resolveUserReference(IdReferenceRequestDTO reference, String label) {
        if (reference == null) return null;
        if (reference.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " id is required");
        }
        return userRepository.findById(reference.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found"));
    }

    private Project resolveProjectReference(IdReferenceRequestDTO reference) {
        if (reference == null) return null;
        if (reference.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project id is required");
        }
        return projectRepository.findById(reference.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Sprint resolveSprintReference(IdReferenceRequestDTO reference) {
        if (reference == null) return null;
        if (reference.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint id is required");
        }
        return sprintRepository.findById(reference.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
    }

    private void validateDeveloperStatusTransition(Long issueId, User actor,
            String currentStatus, String requestedStatus) {
        IssueStatus current = parseStoredStatus(currentStatus);
        IssueStatus target = IssueStatus.valueOf(requestedStatus);
        if (!current.isNextDeveloperStatus(target)) {
            logIssueUpdateDenied(issueId, actor, "developer requested a disallowed status transition");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Developers may move assigned issues from OPEN to IN_PROGRESS, IN_PROGRESS to CODE_REVIEW, and CODE_REVIEW to READY_FOR_TESTING");
        }
    }

    private void validateManagerStatusTransition(String currentStatus, String requestedStatus) {
        IssueStatus current = parseStoredStatus(currentStatus);
        IssueStatus target = IssueStatus.valueOf(requestedStatus);
        if (!current.allowsManagerTransitionTo(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Issues in TESTING can change status only through the test completion endpoint");
        }
    }

    private IssueStatus parseStoredStatus(String currentStatus) {
        // Before status standardization, a missing current value followed the OPEN developer path.
        if (currentStatus == null || currentStatus.isBlank()) return IssueStatus.OPEN;
        try {
            return IssueStatus.fromStored(currentStatus);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Issue has a legacy or invalid status and cannot transition until its status is reviewed");
        }
    }

    private String validateManualStatusTarget(String requestedStatus) {
        IssueStatus status;
        try {
            status = IssueStatus.fromRequest(requestedStatus);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (!status.isManualTarget()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "TESTING and RESOLVED can be set only through the testing workflow");
        }
        return status.name();
    }

    private void validateDeveloperRole(User user) {
        if (user.getRole() == null
                || !"DEVELOPER".equals(user.getRole().trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Assigned user must have the DEVELOPER role");
        }
    }

    private void validateSprintProject(Project project, Sprint sprint) {
        if (sprint == null) return;
        if (project == null || sprint.getProject() == null
                || !Objects.equals(project.getId(), sprint.getProject().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Sprint must belong to the issue project");
        }
    }

}
