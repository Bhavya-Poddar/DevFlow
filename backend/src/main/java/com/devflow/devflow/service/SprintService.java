package com.devflow.devflow.service;

import java.util.List;
import java.util.Optional;

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

    public Sprint createSprint(SprintCreateRequestDTO request) {
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
            sprint.setProject(project);
        }
        return sprintRepository.save(sprint);
    }

    public List<Sprint> getAllSprints() {
        return sprintRepository.findAll();
    }

    public Optional<Sprint> getSprintById(Long id) {
        return sprintRepository.findById(id);
    }

    @Transactional
    public Sprint updateSprint(Long id, SprintUpdateRequestDTO updatedSprint) {
        Sprint existingSprint = sprintRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));

        if (updatedSprint.hasName()) existingSprint.setName(updatedSprint.getName());
        if (updatedSprint.hasGoal()) existingSprint.setGoal(updatedSprint.getGoal());
        if (updatedSprint.hasStartDate()) existingSprint.setStartDate(updatedSprint.getStartDate());
        if (updatedSprint.hasEndDate()) existingSprint.setEndDate(updatedSprint.getEndDate());
        if (updatedSprint.hasStatus()) existingSprint.setStatus(updatedSprint.getStatus());
        if (updatedSprint.hasProject()) {
            IdReferenceRequestDTO projectReference = updatedSprint.getProject();
            if (projectReference == null) {
                existingSprint.setProject(null);
            } else {
                if (projectReference.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project id is required");
                }
                Project project = projectRepository.findById(projectReference.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
                validateProjectChange(existingSprint, project);
                existingSprint.setProject(project);
            }
        }

        // This project has no project membership model, so sprint-start recipients
        // cannot currently be determined safely.

        return sprintRepository.save(existingSprint);
    }

    private void validateProjectChange(Sprint sprint, Project newProject) {
        for (var issue : issueRepository.findBySprintId(sprint.getId())) {
            Long issueProjectId = issue.getProject() == null ? null : issue.getProject().getId();
            Long newProjectId = newProject == null ? null : newProject.getId();
            if (!java.util.Objects.equals(issueProjectId, newProjectId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sprint project cannot change while linked issues belong to another project");
            }
        }
    }

    public void deleteSprint(Long id) {
        if (!sprintRepository.existsById(id)) {
            throw new RuntimeException("Sprint not found");
        }

        sprintRepository.deleteById(id);
    }
}
