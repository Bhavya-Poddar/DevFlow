package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.devflow.devflow.entity.IssueStatus;

class IssueStatusTests {

    @Test
    void onlyTheEightApprovedStatusesCanBeParsed() {
        assertEquals(8, IssueStatus.values().length);
        for (IssueStatus status : IssueStatus.values()) {
            assertEquals(status, IssueStatus.fromRequest(status.name()));
        }
        assertEquals(IssueStatus.CODE_REVIEW, IssueStatus.fromRequest(" code_review "));
        for (String legacyOrMalformed : new String[] {"TODO", "CLOSED", "IN PROGRESS", "OPEN!", "", "  "}) {
            assertThrows(IllegalArgumentException.class, () -> IssueStatus.fromRequest(legacyOrMalformed));
        }
    }

    @Test
    void developerTransitionsAreOnlyTheThreeExistingForwardSteps() {
        assertEquals(true, IssueStatus.OPEN.isNextDeveloperStatus(IssueStatus.IN_PROGRESS));
        assertEquals(true, IssueStatus.IN_PROGRESS.isNextDeveloperStatus(IssueStatus.CODE_REVIEW));
        assertEquals(true, IssueStatus.CODE_REVIEW.isNextDeveloperStatus(IssueStatus.READY_FOR_TESTING));

        for (IssueStatus current : IssueStatus.values()) {
            for (IssueStatus target : IssueStatus.values()) {
                boolean expected = (current == IssueStatus.OPEN && target == IssueStatus.IN_PROGRESS)
                    || (current == IssueStatus.IN_PROGRESS && target == IssueStatus.CODE_REVIEW)
                    || (current == IssueStatus.CODE_REVIEW && target == IssueStatus.READY_FOR_TESTING);
                assertEquals(expected, current.isNextDeveloperStatus(target), current + " -> " + target);
            }
        }
    }

    @Test
    void managerTransitionsPreserveExistingManualMovesButReserveTestingStates() {
        for (IssueStatus current : IssueStatus.values()) {
            for (IssueStatus target : IssueStatus.values()) {
                boolean expected = current.isManualTarget()
                    && (current.isNextDeveloperStatus(target) || target == IssueStatus.IN_PROGRESS && current != IssueStatus.OPEN);
                assertEquals(expected, current.allowsManagerTransitionTo(target), current + " -> " + target);
            }
        }
    }
}
