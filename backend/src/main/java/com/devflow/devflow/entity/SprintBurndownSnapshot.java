package com.devflow.devflow.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sprint_burndown_snapshot",
    uniqueConstraints = @UniqueConstraint(name = "uk_sprint_burndown_date", columnNames = { "sprint_id", "snapshot_date" }))
public class SprintBurndownSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_sprint_burndown_sprint"))
    private Sprint sprint;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(name = "total_issues", nullable = false)
    private long totalIssues;

    @Column(name = "remaining_issues", nullable = false)
    private long remainingIssues;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Sprint getSprint() { return sprint; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public long getTotalIssues() { return totalIssues; }
    public void setTotalIssues(long totalIssues) { this.totalIssues = totalIssues; }
    public long getRemainingIssues() { return remainingIssues; }
    public void setRemainingIssues(long remainingIssues) { this.remainingIssues = remainingIssues; }
}
