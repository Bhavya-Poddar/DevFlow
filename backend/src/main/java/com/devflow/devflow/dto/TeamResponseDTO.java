package com.devflow.devflow.dto;

import java.util.List;

import com.devflow.devflow.entity.Team;

public record TeamResponseDTO(Long id, String name, CollaboratorResponseDTO manager,
        List<CollaboratorResponseDTO> members, Long projectId, String projectName) {
    public TeamResponseDTO(Team team) {
        this(team.getId(), team.getName(), toUser(team.getManager()),
            team.getMembers().stream().map(TeamResponseDTO::toUser)
                .sorted(java.util.Comparator.comparing(CollaboratorResponseDTO::name,
                    java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList(),
            team.getProject() == null ? null : team.getProject().getId(),
            team.getProject() == null ? null : team.getProject().getName());
    }

    private static CollaboratorResponseDTO toUser(com.devflow.devflow.entity.User user) {
        if (user == null) return null;
        return new CollaboratorResponseDTO(user);
    }
}
