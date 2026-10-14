package com.devflow.devflow.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.ProjectCreateRequestDTO;
import com.devflow.devflow.dto.ProjectUpdateRequestDTO;
import com.devflow.devflow.service.ProjectService;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;


    // CREATE PROJECT
    @PostMapping
    public ResponseEntity<Project> createProject(
            @RequestBody ProjectCreateRequestDTO project) {

        Project createdProject =
                projectService.createProject(project);

        return new ResponseEntity<>(
                createdProject,
                HttpStatus.CREATED
        );
    }


    // GET ALL PROJECTS
    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {

        List<Project> projects =
                projectService.getAllProjects();

        return ResponseEntity.ok(projects);
    }


    // GET PROJECT BY ID
    @GetMapping("/{id}")
    public ResponseEntity<Project> getProjectById(
            @PathVariable Long id) {

        Optional<Project> project =
                projectService.getProjectById(id);

        if (project.isPresent()) {

            return ResponseEntity.ok(
                    project.get()
            );
        }

        return ResponseEntity
                .notFound()
                .build();
    }


    // UPDATE PROJECT
    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectUpdateRequestDTO updatedProject) {

        Project project =
                projectService.updateProject(
                        id,
                        updatedProject
                );

        return ResponseEntity.ok(project);
    }


    // DELETE PROJECT
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProject(
            @PathVariable Long id) {

        projectService.deleteProject(id);

        return ResponseEntity.ok(
                "Project deleted successfully"
        );
    }
}
