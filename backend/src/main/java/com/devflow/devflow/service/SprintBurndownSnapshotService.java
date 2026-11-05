package com.devflow.devflow.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.SprintBurndownPointDTO;
import com.devflow.devflow.dto.SprintBurndownResponseDTO;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.SprintBurndownSnapshot;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.SprintBurndownSnapshotRepository;
import com.devflow.devflow.repository.SprintRepository;

@Service
public class SprintBurndownSnapshotService {

    private final SprintRepository sprintRepository;
    private final SprintBurndownSnapshotRepository snapshotRepository;
    private final IssueRepository issueRepository;
    private final Clock clock;

    public SprintBurndownSnapshotService(SprintRepository sprintRepository,
            SprintBurndownSnapshotRepository snapshotRepository,
            IssueRepository issueRepository, Clock clock) {
        this.sprintRepository = sprintRepository;
        this.snapshotRepository = snapshotRepository;
        this.issueRepository = issueRepository;
        this.clock = clock;
    }

    /** Captures each active sprint once for the current UTC date. */
    @Transactional
    public int collectTodaySnapshots() {
        LocalDateTime capturedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate snapshotDate = capturedAt.toLocalDate();
        List<Sprint> activeSprints = sprintRepository.findActiveSprintsForSnapshot(snapshotDate);
        int collected = 0;

        for (Sprint sprint : activeSprints) {
            if (snapshotRepository.existsBySprint_IdAndSnapshotDate(sprint.getId(), snapshotDate)) {
                continue;
            }

            Object[] counts = issueRepository.countSprintBurndownIssues(sprint.getId()).get(0);
            long totalIssues = ((Number) counts[0]).longValue();
            long remainingIssues = counts[1] == null ? 0 : ((Number) counts[1]).longValue();
            SprintBurndownSnapshot snapshot = new SprintBurndownSnapshot();
            snapshot.setSprint(sprint);
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setCapturedAt(capturedAt);
            snapshot.setTotalIssues(totalIssues);
            snapshot.setRemainingIssues(remainingIssues);
            snapshotRepository.save(snapshot);
            collected++;
        }
        return collected;
    }

    @Transactional(readOnly = true)
    public SprintBurndownResponseDTO getBurndown(Long sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        List<SprintBurndownSnapshot> snapshots = snapshotRepository.findBySprint_IdOrderBySnapshotDateAsc(sprintId);
        if (snapshots.isEmpty()) {
            return new SprintBurndownResponseDTO(sprintId, sprint.getName(), sprint.getStartDate(),
                sprint.getEndDate(), null, null, List.of());
        }

        LocalDate trackingStartedOn = snapshots.get(0).getSnapshotDate();
        long baseline = snapshots.get(0).getRemainingIssues();
        long projectionDays = sprint.getEndDate() == null ? 0
            : Math.max(0, ChronoUnit.DAYS.between(trackingStartedOn, sprint.getEndDate()));
        List<SprintBurndownPointDTO> points = snapshots.stream().map(snapshot -> {
            long elapsedDays = ChronoUnit.DAYS.between(trackingStartedOn, snapshot.getSnapshotDate());
            double ideal = projectionDays == 0 ? baseline
                : Math.max(0, baseline * (1.0 - ((double) elapsedDays / projectionDays)));
            return new SprintBurndownPointDTO(snapshot.getSnapshotDate(),
                snapshot.getCapturedAt().toInstant(ZoneOffset.UTC),
                snapshot.getTotalIssues(), snapshot.getRemainingIssues(), ideal);
        }).toList();

        return new SprintBurndownResponseDTO(sprintId, sprint.getName(), sprint.getStartDate(),
            sprint.getEndDate(), trackingStartedOn, baseline, points);
    }
}
