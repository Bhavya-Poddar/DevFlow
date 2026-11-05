package com.devflow.devflow.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devflow.devflow.dto.TeamActivityResponseDTO;
import com.devflow.devflow.repository.AuditLogsRepository;

@Service
public class TeamActivityService {
    private final ProjectAccessService accessService;
    private final AuditLogsRepository auditLogsRepository;
    public TeamActivityService(ProjectAccessService accessService, AuditLogsRepository auditLogsRepository) {
        this.accessService = accessService;
        this.auditLogsRepository = auditLogsRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamActivityResponseDTO> getTeamActivity(Long teamId, String actorEmail, int limit) {
        accessService.requireTeamAccess(teamId, actorEmail);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var auditPage = PageRequest.of(0, safeLimit,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return auditLogsRepository.findTeamActivity(teamId, auditPage).stream()
            .map(TeamActivityResponseDTO::fromAudit).toList();
    }
}
