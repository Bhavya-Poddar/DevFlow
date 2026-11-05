package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.AuditLog;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.service.AuditLogServices;

@ExtendWith(MockitoExtension.class)
class AuditLogSearchTests {
    @Mock private AuditLogsRepository repository;
    private AuditLogServices service;

    @BeforeEach
    void setUp() {
        service = new AuditLogServices();
        ReflectionTestUtils.setField(service, "auditLogRepository", repository);
    }

    @Test
    void appliesFiltersAndStableNewestFirstPagination() {
        User actor = new User();
        actor.setId(7L);
        actor.setName("Audit actor");
        actor.setEmail("private@example.test");
        AuditLog log = new AuditLog();
        log.setId(9L);
        log.setAction("LOGIN_FAILED");
        log.setCreatedAt(LocalDateTime.parse("2026-10-01T10:00:00"));
        log.setDetails("outcome=FAILURE; login credentials rejected");
        log.setUser(actor);
        when(repository.searchAuditLogs(eq(7L), eq("LOGIN_FAILED"), eq(4L), eq("FAILURE"),
                any(), any(), any(Pageable.class)))
            .thenAnswer(call -> new PageImpl<>(List.of(log), call.getArgument(6), 1));

        var result = service.searchLogs(7L, "LOGIN_FAILED", 4L, "failure",
                LocalDateTime.parse("2026-10-01T00:00:00"), LocalDateTime.parse("2026-10-02T00:00:00"), 2, 500);

        assertEquals(201, result.getTotalElements());
        assertEquals("FAILURE", result.getContent().get(0).outcome());
        assertEquals(7L, result.getContent().get(0).actorId());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchAuditLogs(eq(7L), eq("LOGIN_FAILED"), eq(4L), eq("FAILURE"),
                any(), any(), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(List.of("createdAt: DESC", "id: DESC"),
                pageable.getValue().getSort().stream().map(order -> order.getProperty() + ": " + order.getDirection()).toList());
    }

    @Test
    void rejectsUnsupportedOutcomeAndReversedDateRange() {
        ResponseStatusException outcome = assertThrows(ResponseStatusException.class,
                () -> service.searchLogs(null, null, null, "unknown", null, null, 0, 25));
        assertEquals(HttpStatus.BAD_REQUEST, outcome.getStatusCode());
        ResponseStatusException dates = assertThrows(ResponseStatusException.class,
                () -> service.searchLogs(null, null, null, null,
                        LocalDateTime.parse("2026-10-02T00:00:00"),
                        LocalDateTime.parse("2026-10-01T00:00:00"), 0, 25));
        assertEquals(HttpStatus.BAD_REQUEST, dates.getStatusCode());
        verify(repository, never()).searchAuditLogs(any(), any(), any(), any(), any(), any(), any());
    }
}
