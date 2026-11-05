package com.devflow.devflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devflow.devflow.dto.TeamCreateRequestDTO;
import com.devflow.devflow.dto.TeamMemberRequestDTO;
import com.devflow.devflow.dto.TeamResponseDTO;
import com.devflow.devflow.service.TeamService;
import com.devflow.devflow.service.TeamActivityService;
import com.devflow.devflow.dto.TeamActivityResponseDTO;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teamService;
    private final TeamActivityService teamActivityService;

    public TeamController(TeamService teamService, TeamActivityService teamActivityService) {
        this.teamService = teamService;
        this.teamActivityService = teamActivityService;
    }

    @GetMapping
    public ResponseEntity<List<TeamResponseDTO>> list(Authentication authentication) {
        return ResponseEntity.ok(teamService.listTeams(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponseDTO> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(teamService.getTeam(id, authentication.getName()));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<List<TeamActivityResponseDTO>> activity(@PathVariable Long id,
            @RequestParam(defaultValue = "30") int limit, Authentication authentication) {
        return ResponseEntity.ok(teamActivityService.getTeamActivity(id, authentication.getName(), limit));
    }

    @PostMapping
    public ResponseEntity<TeamResponseDTO> create(@RequestBody TeamCreateRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(teamService.createTeam(request, authentication.getName()));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<TeamResponseDTO> addDeveloper(@PathVariable Long id,
            @RequestBody TeamMemberRequestDTO request, Authentication authentication) {
        return ResponseEntity.ok(teamService.addDeveloper(id, request.getDeveloperId(), request.getResponsibility(), authentication.getName()));
    }

    @DeleteMapping("/{id}/members/{developerId}")
    public ResponseEntity<TeamResponseDTO> removeDeveloper(@PathVariable Long id,
            @PathVariable Long developerId, Authentication authentication) {
        return ResponseEntity.ok(teamService.removeDeveloper(id, developerId, authentication.getName()));
    }
}
