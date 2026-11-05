package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.SprintBurndownSnapshot;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.SprintBurndownSnapshotRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.service.SprintBurndownSnapshotService;

@ExtendWith(MockitoExtension.class)
class SprintBurndownSnapshotServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-16T00:05:00Z");

    @Mock private SprintRepository sprintRepository;
    @Mock private SprintBurndownSnapshotRepository snapshotRepository;
    @Mock private IssueRepository issueRepository;

    private SprintBurndownSnapshotService service;
    private Sprint sprint;

    @BeforeEach
    void setUp() {
        service = new SprintBurndownSnapshotService(sprintRepository, snapshotRepository,
            issueRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        sprint = new Sprint();
        sprint.setId(3L);
        sprint.setName("Sprint 3");
        sprint.setStartDate(LocalDate.parse("2026-10-14"));
        sprint.setEndDate(LocalDate.parse("2026-10-20"));
        sprint.setStatus("ACTIVE");
    }

    @Test
    void capturesCurrentIssueCountsOnceForUtcDate() {
        when(sprintRepository.findActiveSprintsForSnapshot(LocalDate.parse("2026-10-16"))).thenReturn(List.of(sprint));
        when(snapshotRepository.existsBySprint_IdAndSnapshotDate(3L, LocalDate.parse("2026-10-16"))).thenReturn(false);
        when(issueRepository.countSprintBurndownIssues(3L)).thenReturn(List.<Object[]>of(new Object[] { 5L, 3L }));
        when(snapshotRepository.save(any(SprintBurndownSnapshot.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals(1, service.collectTodaySnapshots());

        org.mockito.ArgumentCaptor<SprintBurndownSnapshot> saved = org.mockito.ArgumentCaptor.forClass(SprintBurndownSnapshot.class);
        verify(snapshotRepository).save(saved.capture());
        assertEquals(LocalDate.parse("2026-10-16"), saved.getValue().getSnapshotDate());
        assertEquals(NOW.atZone(ZoneOffset.UTC).toLocalDateTime(), saved.getValue().getCapturedAt());
        assertEquals(5, saved.getValue().getTotalIssues());
        assertEquals(3, saved.getValue().getRemainingIssues());
    }

    @Test
    void repeatedCollectionDoesNotCreateDuplicateDailySnapshot() {
        when(sprintRepository.findActiveSprintsForSnapshot(LocalDate.parse("2026-10-16"))).thenReturn(List.of(sprint));
        when(snapshotRepository.existsBySprint_IdAndSnapshotDate(3L, LocalDate.parse("2026-10-16"))).thenReturn(true);

        assertEquals(0, service.collectTodaySnapshots());

        verify(snapshotRepository, never()).save(any());
        verify(issueRepository, never()).countSprintBurndownIssues(3L);
    }

    @Test
    void emptyHistoryHasNoTrackingDateOrFabricatedPoints() {
        when(sprintRepository.findById(3L)).thenReturn(Optional.of(sprint));
        when(snapshotRepository.findBySprint_IdOrderBySnapshotDateAsc(3L)).thenReturn(List.of());

        var response = service.getBurndown(3L);

        assertNull(response.trackingStartedOn());
        assertNull(response.baselineRemainingIssues());
        assertEquals(List.of(), response.snapshots());
        assertEquals(LocalDate.parse("2026-10-14"), response.sprintStartDate());
    }

    @Test
    void responseUsesRecordedHistoryAsTrackingBaselineForIdealLine() {
        SprintBurndownSnapshot first = snapshot("2026-10-16", 4, 4);
        SprintBurndownSnapshot second = snapshot("2026-10-18", 4, 2);
        when(sprintRepository.findById(3L)).thenReturn(Optional.of(sprint));
        when(snapshotRepository.findBySprint_IdOrderBySnapshotDateAsc(3L)).thenReturn(List.of(first, second));

        var response = service.getBurndown(3L);

        assertEquals(LocalDate.parse("2026-10-16"), response.trackingStartedOn());
        assertEquals(4L, response.baselineRemainingIssues());
        assertEquals(2, response.snapshots().size());
        assertEquals(4.0, response.snapshots().get(0).idealRemainingIssues());
        assertEquals(2.0, response.snapshots().get(1).idealRemainingIssues());
        assertEquals(java.time.Instant.parse("2026-10-16T00:05:00Z"), response.snapshots().get(0).capturedAtUtc());
        assertEquals(4L, response.snapshots().get(0).remainingIssues());
        assertEquals(2L, response.snapshots().get(1).remainingIssues());
    }

    @Test
    void unknownSprintReturnsNotFound() {
        when(sprintRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.getBurndown(99L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    private SprintBurndownSnapshot snapshot(String date, long total, long remaining) {
        SprintBurndownSnapshot snapshot = new SprintBurndownSnapshot();
        snapshot.setSprint(sprint);
        snapshot.setSnapshotDate(LocalDate.parse(date));
        snapshot.setCapturedAt(LocalDate.parse(date).atTime(0, 5));
        snapshot.setTotalIssues(total);
        snapshot.setRemainingIssues(remaining);
        return snapshot;
    }
}
