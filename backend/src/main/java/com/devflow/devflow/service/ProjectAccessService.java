package com.devflow.devflow.service;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.UserRepository;

/** Central project/team visibility policy plus database-backed scoped capability checks. */
@Service
public class ProjectAccessService {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final TeamRepository teamRepository;
    private final EffectivePermissionService permissions;

    public ProjectAccessService(UserRepository userRepository, ProjectRepository projectRepository,
            IssueRepository issueRepository, SprintRepository sprintRepository, TeamRepository teamRepository,
            EffectivePermissionService permissions) {
        this.userRepository = userRepository; this.projectRepository = projectRepository;
        this.issueRepository = issueRepository; this.sprintRepository = sprintRepository;
        this.teamRepository = teamRepository; this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authenticated account is no longer available"));
    }

    /** Compatibility helper for callers that need the all-project override, never the recovery flag. */
    public boolean isAdministrator(User user) {
        return user != null && permissions.hasGlobal(user.getEmail(), "PROJECT_READ_ALL");
    }

    public boolean hasGlobalPermission(String email, String permission) {
        return permissions.hasGlobal(email, permission);
    }

    public boolean isTeamAdministrator(User user) {
        return user != null && permissions.hasGlobal(user.getEmail(), "TEAM_READ_ALL");
    }

    public void requireGlobalPermission(String email, String permission) {
        permissions.require(email, permission, null, null);
    }

    @Transactional(readOnly = true)
    public List<Project> visibleProjects(String email) {
        User user = permissions.requireEnabledUser(email);
        if (isAdministrator(user)) return projectRepository.findAll();
        return projectRepository.findVisibleToUser(user.getId()).stream()
            .filter(project -> permissions.has(email, "PROJECT_READ", project.getId(), null)).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> visibleProjectIds(String email) {
        return visibleProjects(email).stream().map(Project::getId).toList();
    }

    @Transactional(readOnly = true)
    public Project requireProjectAccess(Long projectId, String email) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        requireProjectAccess(project, email);
        return project;
    }

    @Transactional(readOnly = true)
    public void requireProjectAccess(Project project, String email) {
        if (project == null) return;
        User user = permissions.requireEnabledUser(email);
        boolean legacyVisible = isAdministrator(user) || project.getTeam() == null
            || isMember(project.getTeam(), user.getId());
        if (!legacyVisible) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "You are not a member of this project's team");
        if (!permissions.has(email, "PROJECT_READ", project.getId(), null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project read permission is not granted");
        }
    }

    @Transactional(readOnly = true)
    public Issue requireIssueAccess(Long issueId, String email) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        requireProjectAccess(issue.getProject(), email);
        return issue;
    }

    /** Checks current task visibility for notification delivery without propagating authorization failures. */
    @Transactional(readOnly = true)
    public boolean canReadIssue(Issue issue, String email) {
        if (issue == null || email == null || email.isBlank()) return false;
        try {
            requireProjectAccess(issue.getProject(), email);
            return true;
        } catch (ResponseStatusException denied) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean canReadProject(Project project, String email) {
        if (project == null || email == null || email.isBlank()) return false;
        try {
            requireProjectAccess(project, email);
            return true;
        } catch (ResponseStatusException denied) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean canReadTeam(Team team, String email) {
        if (team == null || team.getId() == null || email == null || email.isBlank()) return false;
        try {
            User user = permissions.requireEnabledUser(email);
            if (!(permissions.hasGlobal(email, "TEAM_READ_ALL") || isMember(team, user.getId()))) return false;
            permissions.require(email, "TEAM_READ", null, team.getId());
            return true;
        } catch (ResponseStatusException denied) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public void requireIssueProjectChange(Long issueId, Long targetProjectId, String email) {
        Issue issue = requireIssueAccess(issueId, email);
        Long currentId = issue.getProject() == null ? null : issue.getProject().getId();
        if (Objects.equals(currentId, targetProjectId)) return;
        if (currentId != null) permissions.require(email, "PROJECT_UPDATE", currentId, null);
        if (targetProjectId != null) {
            requireProjectAccess(targetProjectId, email);
            permissions.require(email, "PROJECT_UPDATE", targetProjectId, null);
        }
    }

    @Transactional(readOnly = true)
    public void requireSprintProjectChange(Long sprintId, Long targetProjectId, String email) {
        Sprint sprint = requireSprintAccess(sprintId, email);
        Long currentId = sprint.getProject() == null ? null : sprint.getProject().getId();
        if (Objects.equals(currentId, targetProjectId)) return;
        if (currentId != null) permissions.require(email, "PROJECT_UPDATE", currentId, null);
        if (targetProjectId != null) {
            requireProjectAccess(targetProjectId, email);
            permissions.require(email, "PROJECT_UPDATE", targetProjectId, null);
        }
    }

    @Transactional(readOnly = true)
    public Sprint requireSprintAccess(Long sprintId, String email) {
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        requireProjectAccess(sprint.getProject(), email);
        return sprint;
    }

    @Transactional(readOnly = true)
    public Team requireTeamAccess(Long teamId, String email) {
        User user = permissions.requireEnabledUser(email);
        Team team = teamRepository.findDetailedById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (!(permissions.hasGlobal(email, "TEAM_READ_ALL") || isMember(team, user.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this team");
        }
        permissions.require(email, "TEAM_READ", null, teamId);
        return team;
    }

    public void requireCanManageTeam(Team team, User actor) {
        if (team == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        permissions.require(actor.getEmail(), "TEAM_MANAGE", null, team.getId());
    }

    @Transactional(readOnly = true)
    public void requireProjectUpdate(Long projectId, String email) {
        permissions.require(email, "PROJECT_UPDATE", projectId, null);
    }

    public void requireCanAssignProjectToTeam(Team team, User actor) {
        if (team != null) permissions.require(actor.getEmail(), "TEAM_MANAGE", null, team.getId());
    }

    public boolean isMember(Team team, Long userId) {
        if (team == null || userId == null) return false;
        if (team.getManager() != null && userId.equals(team.getManager().getId())) return true;
        return team.getMembers().stream().anyMatch(member -> userId.equals(member.getId()));
    }
}
