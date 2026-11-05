package com.devflow.devflow.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueDependency;
import com.devflow.devflow.entity.IssueStatus;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.entity.AutomationTriggerType;
import com.devflow.devflow.dto.IssueUpdateRequestDTO;
import com.devflow.devflow.dto.IssueCreateRequestDTO;
import com.devflow.devflow.dto.IssueActivityResponseDTO;
import com.devflow.devflow.dto.IssueDependencyCreateRequestDTO;
import com.devflow.devflow.dto.IssueDependencyResponseDTO;
import com.devflow.devflow.dto.IssueResponseDTO;
import com.devflow.devflow.dto.IdReferenceRequestDTO;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IssueService {
    private static final Logger logger = LoggerFactory.getLogger(IssueService.class);

    @Autowired
    private ProjectAccessService projectAccessService;

    @Autowired
    private EffectivePermissionService permissions;

    @Autowired
    private AdminConfigurationService adminConfigurationService;

    private final IssueRepository issueRepository;
    private final IssueDependencyRepository issueDependencyRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final AuditLogServices auditLogService;
    private final NotificationService notificationService;
    private final CommentRepository commentRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final AutomationEventService automationEventService;

    public IssueService(IssueRepository issueRepository, IssueDependencyRepository issueDependencyRepository,
            ProjectRepository projectRepository,
            SprintRepository sprintRepository, UserRepository userRepository,
            AuditLogServices auditLogService, NotificationService notificationService,
            CommentRepository commentRepository, TestExecutionRepository testExecutionRepository,
            AutomationEventService automationEventService) {
        this.issueRepository = issueRepository;
        this.issueDependencyRepository = issueDependencyRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.commentRepository = commentRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.automationEventService = automationEventService;
    }

    @Transactional
    public Issue createIssue(IssueCreateRequestDTO request, String userEmail) {
        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String initialStatus = request.getStatus() == null
            ? IssueStatus.OPEN.name() : validateManualStatusTarget(request.getStatus());

        Issue issue = new Issue();
        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());
        issue.setAcceptanceCriteria(request.getAcceptanceCriteria());
        issue.setPriority(request.getPriority());
        if (adminConfigurationService != null) adminConfigurationService.validatePriority(request.getPriority(), null);
        issue.setStatus(initialStatus);
        issue.setDueDate(request.getDueDate());
        issue.setProject(resolveProjectReference(request.getProject()));
        issue.setSprint(resolveSprintReference(request.getSprint()));
        if (issue.getProject() != null) projectAccessService.requireProjectAccess(issue.getProject(), userEmail);
        requireCapability(userEmail, "ISSUE_CREATE", issue.getProject());
        validateSprintProject(issue.getProject(), issue.getSprint());

        if (!hasCapability(userEmail, "ISSUE_ASSIGN_DEVELOPER", issue.getProject())) {
            issue.setAssignedUser(null);
        } else {
            User assignee = resolveUserReference(request.getAssignedUser(), "Developer");
            if (assignee != null) validateDeveloperRole(assignee, issue.getProject());
            requireTeamMemberForAssignment(issue.getProject(), assignee, "developer");
            issue.setAssignedUser(assignee);
        }
        if (request.getTester() != null) {
            if (!hasCapability(userEmail, "TESTER_ASSIGN", issue.getProject())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tester assignment permission is not granted");
            }
            User tester = resolveTesterReference(request.getTester(), issue.getProject());
            requireTeamMemberForAssignment(issue.getProject(), tester, "tester");
            issue.setTester(tester);
        }
        issue.setReportedBy(actor);

        Issue savedIssue = issueRepository.save(issue);

        if (savedIssue.getAssignedUser() != null) {
            notifyUser(savedIssue.getAssignedUser().getId(), "ISSUE_ASSIGNED",
                "You were assigned task '" + savedIssue.getTitle() + "'", savedIssue.getId(), actor.getId());
        }
        if (savedIssue.getTester() != null) notifyUser(savedIssue.getTester().getId(), "TESTER_ASSIGNED",
            "You have been assigned to test task '" + savedIssue.getTitle() + "'", savedIssue.getId(), actor.getId());

        auditLogService.createLog(
            "ISSUE_CREATED",
            "targetType=ISSUE; targetId=" + savedIssue.getId() + "; task created with status '" + savedIssue.getStatus() + "'",
            savedIssue.getId(),
            actor.getId()
        );
        if (savedIssue.getAssignedUser() != null) {
            auditLogService.createLog("ASSIGNED_USER_CHANGED",
                "targetType=ISSUE; targetId=" + savedIssue.getId() + "; assigneeId="
                    + savedIssue.getAssignedUser().getId(),
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
    public List<IssueResponseDTO> getAllIssueResponses() {
        return getIssueResponses(issueRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<IssueResponseDTO> getAllIssueResponses(String actorEmail) {
        java.util.Set<Long> visibleProjectIds = new java.util.HashSet<>(projectAccessService.visibleProjectIds(actorEmail));
        List<Issue> visibleIssues = issueRepository.findAll().stream()
            .filter(issue -> issue.getProject() == null || visibleProjectIds.contains(issue.getProject().getId()))
            .toList();
        return getIssueResponses(visibleIssues, visibleProjectIds);
    }

    private List<IssueResponseDTO> getIssueResponses(List<Issue> issues) {
        if (issues.isEmpty()) return List.of();
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        Map<Long, List<IssueDependency>> blockedBy = groupByBlockedIssue(
            issueDependencyRepository.findByBlockedIssue_IdIn(issueIds));
        Map<Long, List<IssueDependency>> blocks = groupByBlockingIssue(
            issueDependencyRepository.findByBlockingIssue_IdIn(issueIds));
        return issues.stream().map(issue -> dependencyResponse(issue,
            blockedBy.getOrDefault(issue.getId(), List.of()),
            blocks.getOrDefault(issue.getId(), List.of()))).toList();
    }

    private List<IssueResponseDTO> getIssueResponses(List<Issue> issues, java.util.Set<Long> visibleProjectIds) {
        if (issues.isEmpty()) return List.of();
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();
        Map<Long, List<IssueDependency>> blockedBy = groupByBlockedIssue(
            issueDependencyRepository.findByBlockedIssue_IdIn(issueIds));
        Map<Long, List<IssueDependency>> blocks = groupByBlockingIssue(
            issueDependencyRepository.findByBlockingIssue_IdIn(issueIds));
        return issues.stream().map(issue -> dependencyResponse(issue,
            visibleDependencies(blockedBy.getOrDefault(issue.getId(), List.of()), true, visibleProjectIds),
            visibleDependencies(blocks.getOrDefault(issue.getId(), List.of()), false, visibleProjectIds))).toList();
    }

    private List<IssueDependency> visibleDependencies(List<IssueDependency> dependencies, boolean isBlockingSide,
            java.util.Set<Long> visibleProjectIds) {
        return dependencies.stream().filter(dependency -> isProjectVisible(
            isBlockingSide ? dependency.getBlockingIssue().getProject() : dependency.getBlockedIssue().getProject(),
            visibleProjectIds)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<IssueResponseDTO> getIssueResponse(Long issueId) {
        return issueRepository.findById(issueId).map(this::toIssueResponse);
    }

    @Transactional(readOnly = true)
    public Optional<IssueResponseDTO> getIssueResponse(Long issueId, String actorEmail) {
        return issueRepository.findById(issueId).map(issue -> {
            projectAccessService.requireProjectAccess(issue.getProject(), actorEmail);
            java.util.Set<Long> visibleProjectIds = new java.util.HashSet<>(projectAccessService.visibleProjectIds(actorEmail));
            return toIssueResponse(issue, visibleProjectIds);
        });
    }

    @Transactional(readOnly = true)
    public IssueResponseDTO toIssueResponse(Issue issue) {
        List<IssueDependency> blockedBy = issueDependencyRepository.findByBlockedIssue_Id(issue.getId());
        List<IssueDependency> blocks = issueDependencyRepository.findByBlockingIssue_Id(issue.getId());
        return dependencyResponse(issue, blockedBy, blocks);
    }

    @Transactional(readOnly = true)
    public IssueResponseDTO toIssueResponse(Issue issue, String actorEmail) {
        java.util.Set<Long> visibleProjectIds = new java.util.HashSet<>(projectAccessService.visibleProjectIds(actorEmail));
        return toIssueResponse(issue, visibleProjectIds);
    }

    private IssueResponseDTO toIssueResponse(Issue issue, java.util.Set<Long> visibleProjectIds) {
        List<IssueDependency> blockedBy = issueDependencyRepository.findByBlockedIssue_Id(issue.getId()).stream()
            .filter(dependency -> isProjectVisible(dependency.getBlockingIssue().getProject(), visibleProjectIds))
            .toList();
        List<IssueDependency> blocks = issueDependencyRepository.findByBlockingIssue_Id(issue.getId()).stream()
            .filter(dependency -> isProjectVisible(dependency.getBlockedIssue().getProject(), visibleProjectIds))
            .toList();
        return dependencyResponse(issue, blockedBy, blocks);
    }

    private boolean isProjectVisible(Project project, java.util.Set<Long> visibleProjectIds) {
        return project == null || visibleProjectIds.contains(project.getId());
    }

    private IssueResponseDTO dependencyResponse(Issue issue, List<IssueDependency> blockedBy,
            List<IssueDependency> blocks) {
        List<IssueDependencyResponseDTO> blockingIssues = blockedBy.stream()
            .map(dependency -> new IssueDependencyResponseDTO(
                dependency.getBlockingIssue().getId(), dependency.getBlockingIssue().getTitle(),
                dependency.getBlockingIssue().getStatus(), dependency.getReason()))
            .toList();
        List<IssueDependencyResponseDTO> blockedIssues = blocks.stream()
            .map(dependency -> new IssueDependencyResponseDTO(
                dependency.getBlockedIssue().getId(), dependency.getBlockedIssue().getTitle(),
                dependency.getBlockedIssue().getStatus(), dependency.getReason()))
            .toList();
        return new IssueResponseDTO(issue, blockingIssues, blockedIssues);
    }

    private Map<Long, List<IssueDependency>> groupByBlockedIssue(List<IssueDependency> dependencies) {
        Map<Long, List<IssueDependency>> grouped = new HashMap<>();
        for (IssueDependency dependency : dependencies) {
            grouped.computeIfAbsent(dependency.getBlockedIssue().getId(), ignored -> new ArrayList<>()).add(dependency);
        }
        return grouped;
    }

    private Map<Long, List<IssueDependency>> groupByBlockingIssue(List<IssueDependency> dependencies) {
        Map<Long, List<IssueDependency>> grouped = new HashMap<>();
        for (IssueDependency dependency : dependencies) {
            grouped.computeIfAbsent(dependency.getBlockingIssue().getId(), ignored -> new ArrayList<>()).add(dependency);
        }
        return grouped;
    }

    /** Serializes dependency inserts in this application instance while checking the full graph. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public synchronized IssueResponseDTO addDependency(Long blockedIssueId,
            IssueDependencyCreateRequestDTO request, String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!permissions.hasAnywhere(actorEmail, "ISSUE_DEPENDENCY_MANAGE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task dependency permission is not granted");
        }
        if (request == null || request.getBlockingIssueId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocking task ID is required");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required for the blocker");
        }
        if (reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocker reason must be 500 characters or fewer");
        }
        Long blockingIssueId = request.getBlockingIssueId();
        if (Objects.equals(blockedIssueId, blockingIssueId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A task cannot block itself");
        }

        // Lock issue rows in a stable global order so concurrent links are checked against
        // a current dependency graph and cannot create a cycle across separate requests.
        TreeSet<Long> issueIdsToLock = new TreeSet<>();
        issueRepository.findAll().stream().map(Issue::getId).filter(Objects::nonNull)
            .forEach(issueIdsToLock::add);
        issueIdsToLock.add(blockedIssueId);
        issueIdsToLock.add(blockingIssueId);
        List<Issue> lockedIssues = issueRepository.findAllByIdForUpdate(issueIdsToLock);
        Map<Long, Issue> issuesById = new HashMap<>();
        for (Issue issue : lockedIssues) issuesById.put(issue.getId(), issue);
        Issue blockedIssue = issuesById.get(blockedIssueId);
        Issue blockingIssue = issuesById.get(blockingIssueId);
        if (blockedIssue == null || blockingIssue == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        requireCapability(actor.getEmail(), "ISSUE_DEPENDENCY_MANAGE", blockedIssue.getProject());
        requireCapability(actor.getEmail(), "ISSUE_DEPENDENCY_MANAGE", blockingIssue.getProject());
        projectAccessService.requireProjectAccess(blockedIssue.getProject(), actorEmail);
        projectAccessService.requireProjectAccess(blockingIssue.getProject(), actorEmail);
        if (issueDependencyRepository.findByBlockedIssue_IdAndBlockingIssue_Id(blockedIssueId, blockingIssueId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This blocker link already exists");
        }
        if (wouldCreateDependencyCycle(blockedIssueId, blockingIssueId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This blocker link would create a dependency cycle");
        }

        IssueDependency dependency = new IssueDependency();
        dependency.setBlockedIssue(blockedIssue);
        dependency.setBlockingIssue(blockingIssue);
        dependency.setCreatedBy(actor);
        dependency.setReason(reason);
        issueDependencyRepository.save(dependency);
        auditLogService.createLog("ISSUE_BLOCKER_ADDED",
            "targetType=ISSUE; targetId=" + blockedIssueId + "; blockedByIssueId=" + blockingIssueId,
            blockedIssueId, actor.getId());
        return toIssueResponse(blockedIssue, actorEmail);
    }

    @Transactional
    public void removeDependency(Long blockedIssueId, Long blockingIssueId, String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        IssueDependency dependency = issueDependencyRepository
            .findByBlockedIssue_IdAndBlockingIssue_Id(blockedIssueId, blockingIssueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocker link not found"));
        requireCapability(actorEmail, "ISSUE_DEPENDENCY_MANAGE", dependency.getBlockedIssue().getProject());
        requireCapability(actorEmail, "ISSUE_DEPENDENCY_MANAGE", dependency.getBlockingIssue().getProject());
        projectAccessService.requireProjectAccess(dependency.getBlockedIssue().getProject(), actorEmail);
        projectAccessService.requireProjectAccess(dependency.getBlockingIssue().getProject(), actorEmail);
        issueDependencyRepository.delete(dependency);
        auditLogService.createLog("ISSUE_BLOCKER_REMOVED",
            "targetType=ISSUE; targetId=" + blockedIssueId + "; blockerRemovedIssueId=" + blockingIssueId,
            blockedIssueId, actor.getId());
    }

    private boolean wouldCreateDependencyCycle(Long blockedIssueId, Long blockingIssueId) {
        Map<Long, Set<Long>> blockersByIssue = new HashMap<>();
        for (IssueDependency dependency : issueDependencyRepository.findAll()) {
            blockersByIssue.computeIfAbsent(dependency.getBlockedIssue().getId(), ignored -> new HashSet<>())
                .add(dependency.getBlockingIssue().getId());
        }
        ArrayDeque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.push(blockingIssueId);
        while (!pending.isEmpty()) {
            Long current = pending.pop();
            if (Objects.equals(current, blockedIssueId)) return true;
            if (visited.add(current)) {
                for (Long next : blockersByIssue.getOrDefault(current, Set.of())) pending.push(next);
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<IssueActivityResponseDTO> getIssueActivity(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return auditLogService.getLogsByIssue(issueId).stream()
            .map(IssueActivityResponseDTO::new)
            .toList();
    }

    @Transactional
    public Issue updateIssue(Long id, IssueUpdateRequestDTO updatedIssue, String userEmail) {
        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Issue existingIssue = issueRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        projectAccessService.requireProjectAccess(existingIssue.getProject(), userEmail);

        Long currentProjectId = existingIssue.getProject() == null ? null : existingIssue.getProject().getId();
        boolean canManageAll = permissions.has(userEmail, "ISSUE_UPDATE_ALL", currentProjectId, null);
        boolean isDeveloper = !canManageAll && (permissions.has(userEmail, "ISSUE_UPDATE_ASSIGNED", currentProjectId, null)
            || permissions.has(userEmail, "ISSUE_UPDATE", currentProjectId, null));
        if (!canManageAll && !isDeveloper) {
            logIssueUpdateDenied(id, actor, "no scoped ISSUE_UPDATE permission");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task update permission is not granted");
        }
        if (isDeveloper) {
            boolean assignedToDeveloper = existingIssue.getAssignedUser() != null
                && Objects.equals(actor.getId(), existingIssue.getAssignedUser().getId());

            if (!assignedToDeveloper) {
                logIssueUpdateDenied(id, actor, "issue is not assigned to this developer");
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Developers can only edit tasks assigned to themselves"
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
                    "Developers cannot change a task's assignee, project, or sprint"
                );
            }
        }

        String oldTitle = existingIssue.getTitle();
        String oldDescription = existingIssue.getDescription();
        String oldAcceptanceCriteria = existingIssue.getAcceptanceCriteria();
        String oldPriority = existingIssue.getPriority();
        java.time.LocalDate oldDueDate = existingIssue.getDueDate();
        Long oldProjectId = existingIssue.getProject() == null ? null : existingIssue.getProject().getId();
        String oldStatus = existingIssue.getStatus();
        String newStatus = updatedIssue.hasStatus()
            ? validateManualStatusTarget(updatedIssue.getStatus()) : oldStatus;
        if (isDeveloper && !Objects.equals(oldStatus, newStatus)) {
            validateDeveloperStatusTransition(id, actor, oldStatus, newStatus);
        } else if (updatedIssue.hasStatus() && !Objects.equals(oldStatus, newStatus)) {
            requireCapability(userEmail, "ISSUE_STATUS_MANAGE", existingIssue.getProject());
            validateManagerStatusTransition(oldStatus, newStatus);
        }
        Long oldAssignedUserId = existingIssue.getAssignedUser() != null
            ? existingIssue.getAssignedUser().getId() : null;
        Long oldSprintId = existingIssue.getSprint() == null ? null : existingIssue.getSprint().getId();
        Long oldTesterId = existingIssue.getTester() == null ? null : existingIssue.getTester().getId();

        User updatedTester = existingIssue.getTester();
        if (IssueStatus.TESTING.name().equals(oldStatus) && updatedIssue.hasTester()
                && !Objects.equals(oldTesterId, updatedTester == null ? null : updatedTester.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tester assignment cannot change while testing is in progress");
        }

        Project updatedProject = updatedIssue.hasProject()
            ? resolveProjectReference(updatedIssue.getProject()) : existingIssue.getProject();
        if (updatedIssue.hasTester()) updatedTester = resolveTesterReference(updatedIssue.getTester(), updatedProject);
        Sprint updatedSprint = updatedIssue.hasSprint()
            ? resolveSprintReference(updatedIssue.getSprint()) : existingIssue.getSprint();
        if (updatedIssue.hasProject()) {
            projectAccessService.requireIssueProjectChange(id,
                updatedProject == null ? null : updatedProject.getId(), userEmail);
        }
        if (updatedIssue.hasSprint() && existingIssue.getSprint() != null) {
            projectAccessService.requireSprintProjectChange(existingIssue.getSprint().getId(),
                updatedSprint == null || updatedSprint.getProject() == null
                    ? null : updatedSprint.getProject().getId(), userEmail);
        }
        validateSprintProject(updatedProject, updatedSprint);
        User updatedAssignee = updatedIssue.hasAssignedUser()
            ? resolveUserReference(updatedIssue.getAssignedUser(), "Developer") : existingIssue.getAssignedUser();
        if (updatedAssignee != null) validateDeveloperRole(updatedAssignee, updatedProject);
        if (updatedIssue.hasAssignedUser() && !sameUser(existingIssue.getAssignedUser(), updatedIssue.getAssignedUser())
                && !canManageAll) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task assignment permission is not granted");
        }
        if ((updatedIssue.hasAssignedUser() || updatedIssue.hasProject())
                && hasCapability(userEmail, "ISSUE_ASSIGN_DEVELOPER", updatedProject)) {
            requireTeamMemberForAssignment(updatedProject, updatedAssignee, "developer");
        }
        if ((updatedIssue.hasTester() || updatedIssue.hasProject())
                && hasCapability(userEmail, "TESTER_ASSIGN", updatedProject)) {
            requireTeamMemberForAssignment(updatedProject, updatedTester, "tester");
        }

        if (updatedIssue.hasTitle()) existingIssue.setTitle(updatedIssue.getTitle());
        if (updatedIssue.hasDescription()) existingIssue.setDescription(updatedIssue.getDescription());
        if (updatedIssue.hasAcceptanceCriteria()) existingIssue.setAcceptanceCriteria(updatedIssue.getAcceptanceCriteria());
        if (updatedIssue.hasPriority()) {
            if (adminConfigurationService != null) adminConfigurationService.validatePriority(updatedIssue.getPriority(), oldPriority);
            existingIssue.setPriority(updatedIssue.getPriority());
        }
        if (updatedIssue.hasDueDate()) existingIssue.setDueDate(updatedIssue.getDueDate());
        if (updatedIssue.hasStatus()) existingIssue.setStatus(newStatus);
        if (canManageAll && updatedIssue.hasProject()) {
            existingIssue.setProject(updatedProject);
        }
        if (canManageAll && updatedIssue.hasAssignedUser()) {
            existingIssue.setAssignedUser(updatedAssignee);
        }
        if (canManageAll && updatedIssue.hasSprint()) {
            existingIssue.setSprint(updatedSprint);
        }
        if (updatedIssue.hasTester() && !hasCapability(userEmail, "TESTER_ASSIGN", existingIssue.getProject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tester assignment permission is not granted");
        }
        if (canManageAll && updatedIssue.hasTester()) {
            existingIssue.setTester(updatedTester);
        }

        Issue savedIssue = issueRepository.save(existingIssue);

        recordFieldChange(savedIssue, actor, "TITLE_CHANGED", "Title", oldTitle, savedIssue.getTitle());
        recordFieldChange(savedIssue, actor, "DESCRIPTION_CHANGED", "Description",
            oldDescription, savedIssue.getDescription());
        recordFieldChange(savedIssue, actor, "ACCEPTANCE_CRITERIA_CHANGED", "Acceptance criteria",
            oldAcceptanceCriteria, savedIssue.getAcceptanceCriteria());
        recordFieldChange(savedIssue, actor, "PRIORITY_CHANGED", "Priority",
            oldPriority, savedIssue.getPriority());
        recordFieldChange(savedIssue, actor, "DUE_DATE_CHANGED", "Due date",
            oldDueDate == null ? null : oldDueDate.toString(),
            savedIssue.getDueDate() == null ? null : savedIssue.getDueDate().toString());
        Long newProjectId = savedIssue.getProject() == null ? null : savedIssue.getProject().getId();
        if (!Objects.equals(oldProjectId, newProjectId)) {
            auditLogService.createLog("PROJECT_CHANGED",
                "targetType=ISSUE; targetId=" + savedIssue.getId() + "; projectId changed from "
                    + oldProjectId + " to " + newProjectId,
                savedIssue.getId(), actor.getId());
        }

        if (!Objects.equals(oldStatus, savedIssue.getStatus())) {
            auditLogService.createLog(
                "STATUS_CHANGED",
                "targetType=ISSUE; targetId=" + savedIssue.getId() + "; status changed from '"
                    + oldStatus + "' to '" + savedIssue.getStatus() + "'",
                savedIssue.getId(),
                actor.getId()
            );

            automationEventService.enqueue(AutomationTriggerType.TASK_STATUS_CHANGED, savedIssue,
                oldStatus, savedIssue.getStatus(), "issue-update:" + savedIssue.getId() + ":" + UUID.randomUUID());

            notificationService.notifyIssueParticipants(savedIssue, actor, "ISSUE_STATUS_CHANGED",
                "Task '" + savedIssue.getTitle() + "' changed status to " + savedIssue.getStatus() + ".",
                NotificationTargetPaths.issue(savedIssue.getId()));

        }

        Long newTesterId = savedIssue.getTester() == null ? null : savedIssue.getTester().getId();
        if (!Objects.equals(oldTesterId, newTesterId)) {
            auditLogService.createLog(
                "TESTER_ASSIGNED",
                "targetType=ISSUE; targetId=" + savedIssue.getId() + "; testerId changed from "
                    + oldTesterId + " to " + newTesterId,
                savedIssue.getId(), actor.getId());
            if (savedIssue.getTester() != null) {
                notifyUser(savedIssue.getTester().getId(), "TESTER_ASSIGNED",
                    "You have been assigned to test task '" + savedIssue.getTitle() + "'", savedIssue.getId(), actor.getId());
            }
            if (oldTesterId != null) {
                notifyUser(oldTesterId, "TESTER_UNASSIGNED",
                    "You are no longer assigned to test task '" + savedIssue.getTitle() + "'.",
                    savedIssue.getId(), actor.getId());
            }
        }

        Long newAssignedUserId = savedIssue.getAssignedUser() != null
            ? savedIssue.getAssignedUser().getId() : null;
        if (!Objects.equals(oldAssignedUserId, newAssignedUserId)) {
            auditLogService.createLog(
                "ASSIGNED_USER_CHANGED",
                "targetType=ISSUE; targetId=" + savedIssue.getId() + "; assigneeId changed from "
                    + oldAssignedUserId + " to " + newAssignedUserId,
                savedIssue.getId(),
                actor.getId()
            );
        }

        if (!Objects.equals(oldAssignedUserId, newAssignedUserId) && newAssignedUserId != null) {
            notifyUser(newAssignedUserId, "ISSUE_ASSIGNED",
                "You were assigned task '" + savedIssue.getTitle() + "'", savedIssue.getId(), actor.getId());
        }
        if (Objects.equals(newAssignedUserId, null) && oldAssignedUserId != null) {
            notifyUser(oldAssignedUserId, "ISSUE_UNASSIGNED",
                "Task '" + savedIssue.getTitle() + "' was unassigned from you.", savedIssue.getId(), actor.getId());
        }
        boolean importantDetailsChanged = !Objects.equals(oldTitle, savedIssue.getTitle())
            || !Objects.equals(oldDescription, savedIssue.getDescription())
            || !Objects.equals(oldAcceptanceCriteria, savedIssue.getAcceptanceCriteria())
            || !Objects.equals(oldPriority, savedIssue.getPriority())
            || !Objects.equals(oldDueDate, savedIssue.getDueDate())
            || (updatedIssue.hasSprint() && !Objects.equals(oldSprintId,
                savedIssue.getSprint() == null ? null : savedIssue.getSprint().getId()));
        if (importantDetailsChanged) {
            notificationService.notifyIssueParticipants(savedIssue, actor, "ISSUE_DETAILS_UPDATED",
                "Important details changed for task '" + savedIssue.getTitle() + "'.", NotificationTargetPaths.issue(savedIssue.getId()));
        }

        Long newSprintId = savedIssue.getSprint() == null ? null : savedIssue.getSprint().getId();
        if (!Objects.equals(oldSprintId, newSprintId)) {
            auditLogService.createLog(
                "SPRINT_CHANGED",
                "targetType=ISSUE; targetId=" + savedIssue.getId() + "; sprintId changed from "
                    + oldSprintId + " to " + newSprintId,
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
        if (!permissions.hasAnywhere(userEmail, "ISSUE_DELETE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task deletion permission is not granted");
        }
        Issue issue = issueRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        projectAccessService.requireProjectAccess(issue.getProject(), userEmail);
        requireCapability(userEmail, "ISSUE_DELETE", issue.getProject());

        if (commentRepository.existsByIssueId(id) || testExecutionRepository.existsByIssueId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Task has comments or testing history and cannot be deleted without removing that history");
        }

        issueDependencyRepository.deleteAllForIssue(id);

        // Audit rows are retained after deletion; only their optional issue link is released.
        auditLogService.detachIssueReference(id);
        issueRepository.deleteById(id);

        auditLogService.createLog(
            "ISSUE_DELETED",
            "targetType=ISSUE; targetId=" + id + "; task deleted",
            null,
            actor.getId()
        );
    }

    private boolean hasCapability(String email, String permission, Project project) {
        return permissions.has(email, permission, project == null ? null : project.getId(), null);
    }

    private void requireCapability(String email, String permission, Project project) {
        permissions.require(email, permission, project == null ? null : project.getId(), null);
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
            "targetType=ISSUE; targetId=" + issue.getId() + "; changedField=" + field,
            issue.getId(), actor.getId());
    }

    private void notifyUser(Long userId, String type, String message, Long issueId, Long actorId) {
        try {
            userRepository.findById(userId)
                .filter(user -> user.isEnabled() && !Objects.equals(user.getId(), actorId))
                .ifPresent(user -> notificationService.createNotification(user, type, message,
                    NotificationTargetPaths.issue(issueId)));
        } catch (RuntimeException exception) {
            logger.warn("Could not create {} notification for user {}", type, userId, exception);
        }
    }

    @Transactional
    public Issue assignTester(Long issueId, Long testerId, String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actor not found"));
        if (testerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "testerId is required");
        }
        Issue issue = issueRepository.findByIdForUpdate(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        projectAccessService.requireProjectAccess(issue.getProject(), actorEmail);
        requireCapability(actorEmail, "TESTER_ASSIGN", issue.getProject());
        if (IssueStatus.TESTING.name().equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tester assignment cannot change while testing is in progress");
        }
        User tester = userRepository.findById(testerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tester not found"));
        validateTesterRole(tester, issue.getProject());
        requireTeamMemberForAssignment(issue.getProject(), tester, "tester");

        Long previousTesterId = issue.getTester() == null ? null : issue.getTester().getId();
        issue.setTester(tester);
        Issue savedIssue = issueRepository.save(issue);

        if (!Objects.equals(previousTesterId, tester.getId())) {
            auditLogService.createLog(
                "TESTER_ASSIGNED",
                "targetType=ISSUE; targetId=" + issue.getId() + "; testerId changed from "
                    + previousTesterId + " to " + tester.getId(),
                issue.getId(),
                actor.getId());
            notifyUser(tester.getId(), "TESTER_ASSIGNED",
                "You have been assigned to test task '" + issue.getTitle() + "'", issue.getId(), actor.getId());
            if (previousTesterId != null) {
                notifyUser(previousTesterId, "TESTER_UNASSIGNED",
                    "You are no longer assigned to test task '" + issue.getTitle() + "'.",
                    issue.getId(), actor.getId());
            }
        }
        return savedIssue;
    }

    private User resolveTesterReference(IdReferenceRequestDTO requestedTester, Project project) {
        User tester = resolveUserReference(requestedTester, "Tester");
        if (tester != null) validateTesterRole(tester, project);
        return tester;
    }

    private void validateTesterRole(User tester, Project project) {
        if (!tester.isEnabled()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned tester must be enabled");
        if (project != null && project.getTeam() != null) {
            requireTeamMemberForAssignment(project, tester, "tester");
        } else if (!permissions.hasGlobal(tester.getEmail(), "ASSIGNABLE_TESTER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user must be tester-assignable");
        }
    }

    private void requireTeamMemberForAssignment(Project project, User assignedUser, String relationship) {
        if (project == null || assignedUser == null) return;
        if (project.getTeam() != null) {
            if (!projectAccessService.isMember(project.getTeam(), assignedUser.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assigned " + relationship + " must be a member of the project's team");
            }
            String roleCode = "developer".equals(relationship) ? "DEVELOPER_PROJECT" : "TESTER_PROJECT";
            if (!permissions.hasProjectRole(assignedUser.getEmail(), project.getId(), roleCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assigned " + relationship + " must have the matching project responsibility");
            }
            String teamRoleCode = "developer".equals(relationship) ? "DEVELOPER_TEAM" : "TESTER_TEAM";
            if (!permissions.hasTeamRole(assignedUser.getEmail(), project.getTeam().getId(), teamRoleCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assigned " + relationship + " must have the matching team responsibility");
            }
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
                "Developers may move assigned tasks from OPEN to IN_PROGRESS, IN_PROGRESS to CODE_REVIEW, and CODE_REVIEW to READY_FOR_TESTING");
        }
    }

    private void validateManagerStatusTransition(String currentStatus, String requestedStatus) {
        IssueStatus current = parseStoredStatus(currentStatus);
        IssueStatus target = IssueStatus.valueOf(requestedStatus);
        if (!current.allowsManagerTransitionTo(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tasks in TESTING can change status only through the test completion endpoint");
        }
    }

    private IssueStatus parseStoredStatus(String currentStatus) {
        // Before status standardization, a missing current value followed the OPEN developer path.
        if (currentStatus == null || currentStatus.isBlank()) return IssueStatus.OPEN;
        try {
            return IssueStatus.fromStored(currentStatus);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Task has a legacy or invalid status and cannot transition until its status is reviewed");
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

    private void validateDeveloperRole(User user, Project project) {
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned developer must be enabled");
        }
        if (project == null || project.getTeam() == null) {
            if (permissions.hasGlobal(user.getEmail(), "ASSIGNABLE_DEVELOPER")) return;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Assigned user must be enabled and developer-assignable");
        }
        requireTeamMemberForAssignment(project, user, "developer");
    }

    private void validateSprintProject(Project project, Sprint sprint) {
        if (sprint == null) return;
        if (project == null || sprint.getProject() == null
                || !Objects.equals(project.getId(), sprint.getProject().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Sprint must belong to the task project");
        }
    }

}
