package com.devflow.devflow.dto;

public class UserResponseDTO {

    // Deliberately contains no password field, so API responses cannot expose it.

    private Long id;
    private String name;
    private String email;
    private String role;
    private String avatarUrl;
    private boolean enabled;

    public UserResponseDTO() {
    }

    public UserResponseDTO(
            Long id,
            String name,
            String email,
            String role) {

        this(id, name, email, role, null);
    }

    public UserResponseDTO(Long id, String name, String email, String role, String avatarUrl) {

        this(id, name, email, role, avatarUrl, true);
    }

    public UserResponseDTO(Long id, String name, String email, String role, String avatarUrl, boolean enabled) {

        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.avatarUrl = avatarUrl;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) { 
        this.role = role;
    }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
}
