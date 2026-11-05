package com.devflow.devflow.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.TaskActivityTimelineResponseDTO;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.IssueRepository;

@Service
public class TaskActivityTimelineService {
    private final IssueRepository issueRepository;
    private final AuditLogsRepository auditLogsRepository;
    public TaskActivityTimelineService(IssueRepository issueRepository, AuditLogsRepository auditLogsRepository) {
        this.issueRepository = issueRepository;
        this.auditLogsRepository = auditLogsRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskActivityTimelineResponseDTO> getTimeline(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return auditLogsRepository.findByIssueIdOrderByCreatedAtAscIdAsc(issueId).stream()
            .map(TaskActivityTimelineResponseDTO::fromAudit).toList();
    }
}
