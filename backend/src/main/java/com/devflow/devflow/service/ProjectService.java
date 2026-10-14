package com.devflow.devflow.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.ProjectCreateRequestDTO;
import com.devflow.devflow.dto.ProjectUpdateRequestDTO;
import com.devflow.devflow.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    @Autowired
    private ProjectRepository projectRepository;

    public Project createProject(ProjectCreateRequestDTO request) {
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setStatus(request.getStatus());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        return projectRepository.save(project);
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    public Project updateProject(Long id, ProjectUpdateRequestDTO updatedProject) {
        Project existingProject = projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (updatedProject.hasName()) existingProject.setName(updatedProject.getName());
        if (updatedProject.hasDescription()) existingProject.setDescription(updatedProject.getDescription());
        if (updatedProject.hasStatus()) existingProject.setStatus(updatedProject.getStatus());
        if (updatedProject.hasStartDate()) existingProject.setStartDate(updatedProject.getStartDate());
        if (updatedProject.hasEndDate()) existingProject.setEndDate(updatedProject.getEndDate());

        return projectRepository.save(existingProject);
    }

    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }

        projectRepository.deleteById(id);
    }
}
