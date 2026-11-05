package com.devflow.devflow.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "project_role_assignment", uniqueConstraints = @UniqueConstraint(
    name = "uk_project_role_assignment", columnNames = {"project_id", "user_id"}))
public class ProjectRoleAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "role_id", nullable = false)
    private AuthorizationRole role;

    protected ProjectRoleAssignment() {}
    public ProjectRoleAssignment(Project project, User user, AuthorizationRole role) {
        this.project = project; this.user = user; this.role = role;
    }
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public User getUser() { return user; }
    public AuthorizationRole getRole() { return role; }
    public void setRole(AuthorizationRole role) { this.role = role; }
}
