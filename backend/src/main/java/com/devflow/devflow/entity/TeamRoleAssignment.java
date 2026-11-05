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
@Table(name = "team_role_assignment", uniqueConstraints = @UniqueConstraint(
    name = "uk_team_role_assignment", columnNames = {"team_id", "user_id"}))
public class TeamRoleAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "role_id", nullable = false)
    private AuthorizationRole role;

    protected TeamRoleAssignment() {}
    public TeamRoleAssignment(Team team, User user, AuthorizationRole role) {
        this.team = team; this.user = user; this.role = role;
    }
    public Long getId() { return id; }
    public Team getTeam() { return team; }
    public User getUser() { return user; }
    public AuthorizationRole getRole() { return role; }
    public void setRole(AuthorizationRole role) { this.role = role; }
}
