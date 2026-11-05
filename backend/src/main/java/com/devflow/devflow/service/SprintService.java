package com.devflow.devflow.service;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.IdReferenceRequestDTO;
import com.devflow.devflow.dto.SprintCreateRequestDTO;
import com.devflow.devflow.dto.SprintUpdateRequestDTO;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.SprintBurndownSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintService {

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private SprintBurndownSnapshotRepository burndownSnapshotRepository;

    @Autowired private AuditLogServices auditLogService;
    @Autowired private ProjectAccessService projectAccessService;

    public Sprint createSprint(SprintCreateRequestDTO request) {
        return createSprint(request, null);
    }

    @Transactional
    public Sprint createSprint(SprintCreateRequestDTO request, String actorEmail) {
        Sprint sprint = new Sprint();
        sprint.setName(request.getName());
        sprint.setGoal(request.getGoal());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setStatus(request.getStatus());
        IdReferenceRequestDTO projectReference = request.getProject();
        if (projectReference != null) {
            if (projectReference.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project id is required");
            }
            Project project = projectRepository.findById(projectReference.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
            if (actorEmail != null) projectAccessService.requireProjectAccess(project, actorEmail);
            sprint.setProject(project);
        }
        Sprint saved = sprintRepository.save(sprint);
        if (actorEmail != null) {
            Long actorId = auditLogService.findActorId(actorEmail);
            auditLogService.createLog("SPRINT_CREATED", "targetType=SPRINT; targetId=" + saved.getId()
                    + "; sprint created", null, actorId);
        }
        return saved;
    }

    public List<Sprint> getAllSprints() {
        return sprintRepository.findAll();
    }

    public List<Sprint> getAllSprints(String actorEmail) {
        java.util.Set<Long> visibleProjectIds = new java.util.HashSet<>(projectAccessService.visibleProjectIds(actorEmail));
        return sprintRepository.findAll().stream()
            .filter(sprint -> sprint.getProject() == null || visibleProjectIds.contains(sprint.getProject().getId()))
            .toList();
    }

    public Optional<Sprint> getSprintById(Long id) {
        return sprintRepository.findById(id);
    }

    @Transactional
    public Sprint updateSprint(Long id, SprintUpdateRequestDTO updatedSprint) {
        return updateSprint(id, updatedSprint, null);
    }

    @Transactional
    public Sprint updateSprint(Long id, SprintUpdateRequestDTO updatedSprint, String actorEmail) {
        Sprint existingSprint = sprintRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        if (actorEmail != null) projectAccessService.requireProjectAccess(existingSprint.getProject(), actorEmail);

        var changedFields = new ArrayList<String>();

        if (updatedSprint.hasName()) { if (!Objects.equals(existingSprint.getName(), updatedSprint.getName())) changedFields.add("name"); existingSprint.setName(updatedSprint.getName()); }
        if (updatedSprint.hasGoal()) { if (!Objects.equals(existingSprint.getGoal(), updatedSprint.getGoal())) changedFields.add("goal"); existingSprint.setGoal(updatedSprint.getGoal()); }
        if (updatedSprint.hasStartDate()) { if (!Objects.equals(existingSprint.getStartDate(), updatedSprint.getStartDate())) changedFields.add("startDate"); existingSprint.setStartDate(updatedSprint.getStartDate()); }
        if (updatedSprint.hasEndDate()) { if (!Objects.equals(existingSprint.getEndDate(), updatedSprint.getEndDate())) changedFields.add("endDate"); existingSprint.setEndDate(updatedSprint.getEndDate()); }
        if (updatedSprint.hasStatus()) { if (!Objects.equals(existingSprint.getStatus(), updatedSprint.getStatus())) changedFields.add("status"); existingSprint.setStatus(updatedSprint.getStatus()); }
        if (updatedSprint.hasProject()) {
            IdReferenceRequestDTO projectReference = updatedSprint.getProject();
            if (projectReference == null) {
                if (actorEmail != null) projectAccessService.requireSprintProjectChange(id, null, actorEmail);
                if (existingSprint.getProject() != null) changedFields.add("project");
                existingSprint.setProject(null);
            } else {
                if (projectReference.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project id is required");
                }
                Project project = projectRepository.findById(projectReference.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
                if (actorEmail != null) projectAccessService.requireSprintProjectChange(id, project.getId(), actorEmail);
                validateProjectChange(existingSprint, project);
                if (!Objects.equals(existingSprint.getProject() == null ? null : existingSprint.getProject().getId(), project.getId())) changedFields.add("project");
                existingSprint.setProject(project);
            }
        }

        // This project has no project membership model, so sprint-start recipients
        // cannot currently be determined safely.

        Sprint saved = sprintRepository.save(existingSprint);
        if (actorEmail != null && !changedFields.isEmpty()) {
            auditLogService.createLog("SPRINT_UPDATED", "targetType=SPRINT; targetId=" + id
                    + "; updatedFields=" + String.join(",", changedFields), null,
                    auditLogService.findActorId(actorEmail));
        }
        return saved;
    }

    private void validateProjectChange(Sprint sprint, Project newProject) {
        for (var issue : issueRepository.findBySprintId(sprint.getId())) {
            Long issueProjectId = issue.getProject() == null ? null : issue.getProject().getId();
            Long newProjectId = newProject == null ? null : newProject.getId();
            if (!java.util.Objects.equals(issueProjectId, newProjectId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sprint project cannot change while linked tasks belong to another project");
            }
        }
    }

    @Transactional
    public void deleteSprint(Long id) {
        deleteSprint(id, null);
    }

    @Transactional
    public void deleteSprint(Long id, String actorEmail) {
        sprintRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        if (burndownSnapshotRepository.existsBySprint_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Sprint has burndown history and cannot be deleted");
        }

        sprintRepository.deleteById(id);
        if (actorEmail != null) {
            auditLogService.createLog("SPRINT_DELETED", "targetType=SPRINT; targetId=" + id + "; sprint deleted",
                    null, auditLogService.findActorId(actorEmail));
        }
    }
}
