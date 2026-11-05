package com.devflow.devflow.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.dto.SprintCreateRequestDTO;
import com.devflow.devflow.dto.SprintUpdateRequestDTO;
import com.devflow.devflow.service.SprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/sprints")
@Tag(name = "Sprints", description = "Sprint planning and lifecycle operations.")
@SecurityRequirement(name = "bearerAuth")
public class SprintController {

    @Autowired
    private SprintService sprintService;

    @PostMapping
    @Operation(summary = "Create a sprint", description = "Creates a sprint and associates it with an existing project.")
    @ApiResponse(responseCode = "201", description = "Sprint created.")
    public ResponseEntity<Sprint> createSprint(@RequestBody SprintCreateRequestDTO sprint, Authentication authentication) {
        Sprint createdSprint = sprintService.createSprint(sprint, authentication.getName());
        return new ResponseEntity<>(createdSprint, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List sprints", description = "Returns the sprints available to the authenticated application user.")
    public ResponseEntity<List<Sprint>> getAllSprints(Authentication authentication) {
        return ResponseEntity.ok(sprintService.getAllSprints(authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sprint", description = "Returns a sprint by ID or 404 when missing.")
    public ResponseEntity<Sprint> getSprintById(@PathVariable Long id) {
        Optional<Sprint> sprint = sprintService.getSprintById(id);

        if (sprint.isPresent()) {
            return ResponseEntity.ok(sprint.get());
        }

        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a sprint", description = "Updates supplied sprint fields while preserving fields omitted from the request.")
    public ResponseEntity<Sprint> updateSprint(
            @PathVariable Long id,
            @RequestBody SprintUpdateRequestDTO updatedSprint, Authentication authentication) {

        Sprint sprint = sprintService.updateSprint(id, updatedSprint, authentication.getName());
        return ResponseEntity.ok(sprint);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a sprint", description = "Deletes a sprint if no retained burndown history or other relationship blocks deletion.")
    public ResponseEntity<String> deleteSprint(@PathVariable Long id, Authentication authentication) {
        sprintService.deleteSprint(id, authentication.getName());
        return ResponseEntity.ok("Sprint deleted successfully");
    }
}
