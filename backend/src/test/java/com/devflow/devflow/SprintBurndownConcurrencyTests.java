package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.SprintBurndownSnapshotRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.service.SprintBurndownSnapshotService;

@SpringBootTest
class SprintBurndownConcurrencyTests {
    @Autowired private SprintRepository sprintRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private SprintBurndownSnapshotRepository snapshotRepository;
    @Autowired private SprintBurndownSnapshotService snapshotService;

    @Test
    void concurrentCollectorsPersistOnlyOneSnapshotForSprintAndUtcDate() throws Exception {
        LocalDate today = LocalDate.now(java.time.Clock.systemUTC());
        Sprint sprint = new Sprint();
        sprint.setName("Snapshot concurrency test");
        sprint.setStatus("ACTIVE");
        sprint.setStartDate(today);
        sprint.setEndDate(today.plusDays(5));
        sprint = sprintRepository.saveAndFlush(sprint);

        Issue issue = new Issue();
        issue.setTitle("Snapshot test issue");
        issue.setStatus("OPEN");
        issue.setSprint(sprint);
        issueRepository.saveAndFlush(issue);
        Issue resolvedIssue = new Issue();
        resolvedIssue.setTitle("Resolved snapshot test issue");
        resolvedIssue.setStatus("RESOLVED");
        resolvedIssue.setSprint(sprint);
        issueRepository.saveAndFlush(resolvedIssue);
        Issue closedIssue = new Issue();
        closedIssue.setTitle("Legacy closed snapshot test issue");
        closedIssue.setStatus("CLOSED");
        closedIssue.setSprint(sprint);
        issueRepository.saveAndFlush(closedIssue);
        Long sprintId = sprint.getId();
        long persistedSnapshotCount = 0;
        long totalIssues = 0;
        long remainingIssues = 0;

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> collect(ready, start));
            var second = executor.submit(() -> collect(ready, start));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            persistedSnapshotCount = snapshotRepository.countBySprint_Id(sprintId);
            var snapshot = snapshotRepository.findBySprint_IdOrderBySnapshotDateAsc(sprintId).get(0);
            totalIssues = snapshot.getTotalIssues();
            remainingIssues = snapshot.getRemainingIssues();
        } finally {
            snapshotRepository.deleteAll(snapshotRepository.findBySprint_IdOrderBySnapshotDateAsc(sprintId));
            issueRepository.deleteAll(issueRepository.findBySprintId(sprintId));
            sprintRepository.delete(sprint);
        }

        assertEquals(1, persistedSnapshotCount);
        assertEquals(3, totalIssues);
        assertEquals(1, remainingIssues);
    }

    private int collect(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Collection test did not start");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return snapshotService.collectTodaySnapshots();
    }
}
