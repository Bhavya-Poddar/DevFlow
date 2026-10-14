package com.devflow.devflow.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.devflow.devflow.util.EmailAddress;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String role;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean enabled = true;

    @JsonIgnore
    @Column(name = "avatar_key", length = 64)
    private String avatarKey;

    public User() {
    }

    public User(Long id, String name, String email, String password, String role) {
        this.id = id;
        this.name = name;
        this.email = EmailAddress.normalize(email);
        this.password = password;
        this.role = role;
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
        this.email = EmailAddress.normalize(email);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }	

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @JsonIgnore
    public String getAvatarKey() { return avatarKey; }

    @JsonIgnore
    public void setAvatarKey(String avatarKey) { this.avatarKey = avatarKey; }

    @JsonProperty("avatarUrl")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getAvatarUrl() {
        return avatarKey == null ? null : "/api/users/" + id + "/avatar";
    }
}
