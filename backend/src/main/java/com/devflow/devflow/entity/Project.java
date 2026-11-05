package com.devflow.devflow.entity;

import java.time.LocalDate;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "project", uniqueConstraints = @UniqueConstraint(name = "uk_project_team", columnNames = "team_id"))
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String status;
    @Column(length = 64)
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;

    @Column(name = "github_repository_owner", length = 39)
    private String githubRepositoryOwner;

    @Column(name = "github_repository_name", length = 100)
    private String githubRepositoryName;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_project_team"))
    private Team team;

    public Project() {}

    public Project(Long id, String name, String description, String status, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    @JsonIgnore public String getGithubRepositoryOwner() { return githubRepositoryOwner; }
    public void setGithubRepositoryOwner(String githubRepositoryOwner) { this.githubRepositoryOwner = githubRepositoryOwner; }

    @JsonIgnore public String getGithubRepositoryName() { return githubRepositoryName; }
    public void setGithubRepositoryName(String githubRepositoryName) { this.githubRepositoryName = githubRepositoryName; }

    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }

    @JsonProperty("teamId")
    public Long getTeamId() { return team == null ? null : team.getId(); }

    @JsonProperty("teamName")
    public String getTeamName() { return team == null ? null : team.getName(); }
}
