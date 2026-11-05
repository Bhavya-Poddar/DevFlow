package com.devflow.devflow.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.ProjectCreateRequestDTO;
import com.devflow.devflow.dto.ProjectUpdateRequestDTO;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired private AuditLogServices auditLogService;
    @Autowired private TeamRepository teamRepository;
    @Autowired private ProjectAccessService projectAccessService;
    @Autowired private AdminConfigurationService adminConfigurationService;
    @Autowired private ProjectTeamProvisioningService teamProvisioningService;
    @Autowired private NotificationService notificationService;
    @Autowired private com.devflow.devflow.repository.UserRepository userRepository;

    public Project createProject(ProjectCreateRequestDTO request) {
        return createProject(request, null);
    }

    @Transactional
    public Project createProject(ProjectCreateRequestDTO request, String actorEmail) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project details are required");
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        if (actorEmail != null && (request.getTeamComposition() == null || request.getTeamComposition().getManagerUserId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A project team and its Project Manager, Team Lead, Developer, and Tester responsibilities are required");
        }
        if (actorEmail != null && request.getTeamId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A project team is created from the selected team composition; an existing team cannot be assigned during project creation");
        }
        if (actorEmail != null) {
            projectAccessService.requireGlobalPermission(actorEmail, "PROJECT_CREATE");
            teamProvisioningService.validateComposition(request.getTeamComposition());
        }
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setStatus(request.getStatus());
        if (request.getCategory() != null && adminConfigurationService != null) {
            adminConfigurationService.validateProjectCategory(request.getCategory(), null);
        }
        project.setCategory(normalizeCategory(request.getCategory()));
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        if (request.getTeamId() != null) {
            Team team = teamRepository.findDetailedById(request.getTeamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
            if (actorEmail != null) {
                User actor = projectAccessService.requireUser(actorEmail);
                projectAccessService.requireCanAssignProjectToTeam(team, actor);
            }
            project.setTeam(team);
        }
        Project saved = projectRepository.save(project);
        if (actorEmail != null) {
            String teamName = request.getTeamName() == null || request.getTeamName().isBlank()
                ? request.getName().trim() + " Team" : request.getTeamName().trim();
            teamProvisioningService.createForProject(saved, teamName, request.getTeamComposition());
            saved = projectRepository.save(saved);
        }
        if (actorEmail != null) {
            Long actorId = auditLogService.findActorId(actorEmail);
            auditLogService.createLog("PROJECT_CREATED", "targetType=PROJECT; targetId=" + saved.getId()
                    + "; project created", null, actorId);
            if (notificationService != null) {
                User actor = userRepository == null ? null : userRepository.findByEmailIgnoreCase(actorEmail).orElse(null);
                String target = NotificationTargetPaths.project(saved.getId());
                notificationService.notifyProjectParticipants(saved, actor, "PROJECT_CREATED",
                    "Project '" + saved.getName() + "' was created and you are part of its project team.", target);
            }
        }
        return saved;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public List<Project> getAllProjects(String actorEmail) {
        return projectAccessService.visibleProjects(actorEmail);
    }

    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    public Optional<Project> getProjectById(Long id, String actorEmail) {
        return projectRepository.findById(id).map(project -> {
            projectAccessService.requireProjectAccess(project, actorEmail);
            return project;
        });
    }

    public Project updateProject(Long id, ProjectUpdateRequestDTO updatedProject) {
        return updateProject(id, updatedProject, null);
    }

    @Transactional
    public Project updateProject(Long id, ProjectUpdateRequestDTO updatedProject, String actorEmail) {
        Project existingProject = projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (actorEmail != null) projectAccessService.requireProjectAccess(existingProject, actorEmail);

        List<String> changedFields = new ArrayList<>();

        if (updatedProject.hasName()) { if (!Objects.equals(existingProject.getName(), updatedProject.getName())) changedFields.add("name"); existingProject.setName(updatedProject.getName()); }
        if (updatedProject.hasDescription()) { if (!Objects.equals(existingProject.getDescription(), updatedProject.getDescription())) changedFields.add("description"); existingProject.setDescription(updatedProject.getDescription()); }
        if (updatedProject.hasStatus()) { if (!Objects.equals(existingProject.getStatus(), updatedProject.getStatus())) changedFields.add("status"); existingProject.setStatus(updatedProject.getStatus()); }
        if (updatedProject.hasCategory()) {
            String nextCategory = normalizeCategory(updatedProject.getCategory());
            if (adminConfigurationService != null) {
                adminConfigurationService.validateProjectCategory(nextCategory, existingProject.getCategory());
            }
            if (!Objects.equals(existingProject.getCategory(), nextCategory)) changedFields.add("category");
            existingProject.setCategory(nextCategory);
        }
        if (updatedProject.hasStartDate()) { if (!Objects.equals(existingProject.getStartDate(), updatedProject.getStartDate())) changedFields.add("startDate"); existingProject.setStartDate(updatedProject.getStartDate()); }
        if (updatedProject.hasEndDate()) { if (!Objects.equals(existingProject.getEndDate(), updatedProject.getEndDate())) changedFields.add("endDate"); existingProject.setEndDate(updatedProject.getEndDate()); }

        if (updatedProject.hasTeamId()) {
            if (actorEmail != null) projectAccessService.requireProjectUpdate(id, actorEmail);
            Team oldTeam = existingProject.getTeam();
            Team newTeam = updatedProject.getTeamId() == null ? null : teamRepository.findDetailedById(updatedProject.getTeamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
            Long oldTeamId = oldTeam == null ? null : oldTeam.getId();
            Long newTeamId = newTeam == null ? null : newTeam.getId();
            if (!Objects.equals(oldTeamId, newTeamId)) {
                if (newTeam == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A project team cannot be detached. Map or retain the existing team to preserve membership and access.");
                }
                if (projectRepository.existsByTeam_Id(newTeamId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This team is already associated with a project");
                }
                if (oldTeam != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A project team cannot be replaced after association; preserve the established team membership.");
                }
                if (actorEmail != null) {
                    User actor = projectAccessService.requireUser(actorEmail);
                    if (oldTeam != null) projectAccessService.requireCanManageTeam(oldTeam, actor);
                    if (newTeam != null) projectAccessService.requireCanAssignProjectToTeam(newTeam, actor);
                }
                teamProvisioningService.validateExistingMapping(existingProject,newTeam);
                existingProject.setTeam(newTeam);
                changedFields.add("team");
            }
        }

        Project saved = projectRepository.save(existingProject);
        if (actorEmail != null && !changedFields.isEmpty()) {
            Long actorId = auditLogService.findActorId(actorEmail);
            auditLogService.createLog("PROJECT_UPDATED", "targetType=PROJECT; targetId=" + id
                    + "; updatedFields=" + String.join(",", changedFields), null, actorId);
            User actor = userRepository == null ? null : userRepository.findByEmailIgnoreCase(actorEmail).orElse(null);
            String details = changedFields.contains("status")
                ? "Project '" + saved.getName() + "' status changed to " + saved.getStatus() + "."
                : "Project '" + saved.getName() + "' details were updated (" + String.join(", ", changedFields) + ").";
            if (notificationService != null) notificationService.notifyProjectParticipants(saved, actor, "PROJECT_UPDATED", details,
                NotificationTargetPaths.project(id));
        }
        return saved;
    }

    public void deleteProject(Long id) {
        deleteProject(id, null);
    }

    @Transactional
    public void deleteProject(Long id, String actorEmail) {
        if (!projectRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }

        projectRepository.deleteById(id);
        if (actorEmail != null) {
            auditLogService.createLog("PROJECT_DELETED", "targetType=PROJECT; targetId=" + id + "; project deleted",
                    null, auditLogService.findActorId(actorEmail));
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return null;
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid project category");
        }
        return normalized;
    }

}
