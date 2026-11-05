package com.devflow.devflow.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "team", uniqueConstraints = @UniqueConstraint(name = "uk_team_name", columnNames = "name"))
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_team_manager"))
    private User manager;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "team_member",
        joinColumns = @JoinColumn(name = "team_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_team_member_team")),
        inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_team_member_user")),
        uniqueConstraints = @UniqueConstraint(name = "pk_team_member", columnNames = { "team_id", "user_id" }))
    private Set<User> members = new LinkedHashSet<>();

    /** Inverse of the unique project.team_id association; older unmapped teams remain null. */
    @JsonIgnore
    @OneToOne(mappedBy = "team", fetch = FetchType.LAZY)
    private Project project;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public User getManager() { return manager; }
    public void setManager(User manager) { this.manager = manager; }
    public Set<User> getMembers() { return members; }
    public void setMembers(Set<User> members) { this.members = members; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
}
