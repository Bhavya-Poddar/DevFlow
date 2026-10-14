package com.devflow.devflow.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.dto.SprintCreateRequestDTO;
import com.devflow.devflow.dto.SprintUpdateRequestDTO;
import com.devflow.devflow.service.SprintService;

@RestController
@RequestMapping("/api/sprints")
public class SprintController {

    @Autowired
    private SprintService sprintService;

    @PostMapping
    public ResponseEntity<Sprint> createSprint(@RequestBody SprintCreateRequestDTO sprint) {
        Sprint createdSprint = sprintService.createSprint(sprint);
        return new ResponseEntity<>(createdSprint, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Sprint>> getAllSprints() {
        return ResponseEntity.ok(sprintService.getAllSprints());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sprint> getSprintById(@PathVariable Long id) {
        Optional<Sprint> sprint = sprintService.getSprintById(id);

        if (sprint.isPresent()) {
            return ResponseEntity.ok(sprint.get());
        }

        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sprint> updateSprint(
            @PathVariable Long id,
            @RequestBody SprintUpdateRequestDTO updatedSprint) {

        Sprint sprint = sprintService.updateSprint(id, updatedSprint);
        return ResponseEntity.ok(sprint);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteSprint(@PathVariable Long id) {
        sprintService.deleteSprint(id);
        return ResponseEntity.ok("Sprint deleted successfully");
    }
}
