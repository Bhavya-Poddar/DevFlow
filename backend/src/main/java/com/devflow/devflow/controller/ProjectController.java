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
import org.springframework.security.core.Authentication;

import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.ProjectCreateRequestDTO;
import com.devflow.devflow.dto.ProjectUpdateRequestDTO;
import com.devflow.devflow.service.ProjectService;
import com.devflow.devflow.service.ProjectAssignableMemberService;
import com.devflow.devflow.dto.ProjectAssignableMemberDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project lifecycle and metadata operations.")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    @Autowired
    private ProjectService projectService;
    @Autowired private ProjectAssignableMemberService assignableMemberService;


    // CREATE PROJECT
    @PostMapping
    @Operation(summary = "Create a project", description = "Creates a project from the supplied project fields.")
    @ApiResponse(responseCode = "201", description = "Project created.")
    public ResponseEntity<Project> createProject(
            @RequestBody ProjectCreateRequestDTO project, Authentication authentication) {

        Project createdProject =
                projectService.createProject(project, authentication.getName());

        return new ResponseEntity<>(
                createdProject,
                HttpStatus.CREATED
        );
    }


    // GET ALL PROJECTS
    @GetMapping
    @Operation(summary = "List projects", description = "Returns the projects visible through the authenticated API.")
    public ResponseEntity<List<Project>> getAllProjects(Authentication authentication) {

        List<Project> projects =
                projectService.getAllProjects(authentication.getName());

        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}/assignable-members")
    public ResponseEntity<List<ProjectAssignableMemberDTO>> getAssignableMembers(@PathVariable Long id,Authentication authentication){
        return ResponseEntity.ok(assignableMemberService.list(id,authentication.getName()));
    }


    // GET PROJECT BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get a project", description = "Returns a project by ID or 404 if it does not exist.")
    public ResponseEntity<Project> getProjectById(
            @PathVariable Long id, Authentication authentication) {

        Optional<Project> project =
                projectService.getProjectById(id, authentication.getName());

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
    @Operation(summary = "Update a project", description = "Updates allowed project metadata for an authorized project manager or administrator.")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectUpdateRequestDTO updatedProject, Authentication authentication) {

        Project project =
                projectService.updateProject(
                        id,
                        updatedProject,
                        authentication.getName()
                );

        return ResponseEntity.ok(project);
    }


    // DELETE PROJECT
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project", description = "Deletes a project when existing relationship and authorization rules allow it.")
    public ResponseEntity<String> deleteProject(
            @PathVariable Long id, Authentication authentication) {

        projectService.deleteProject(id, authentication.getName());

        return ResponseEntity.ok(
                "Project deleted successfully"
        );
    }
}
