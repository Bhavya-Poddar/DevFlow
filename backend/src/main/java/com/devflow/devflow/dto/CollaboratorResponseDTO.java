package com.devflow.devflow.dto;

import com.devflow.devflow.entity.User;

/** Minimal identity fields needed to assign work and render collaborators. */
public record CollaboratorResponseDTO(Long id, String name, String role, String avatarUrl) {
    public CollaboratorResponseDTO(User user) {
        this(user.getId(), user.getName(), user.getRole(), user.getAvatarUrl());
    }
}
