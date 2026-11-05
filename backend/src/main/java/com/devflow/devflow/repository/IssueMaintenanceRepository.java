package com.devflow.devflow.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.IssueMaintenance;

public interface IssueMaintenanceRepository extends JpaRepository<IssueMaintenance, Long> {
    Optional<IssueMaintenance> findByIssueId(Long issueId);
    List<IssueMaintenance> findByStatusAndExpiresAtLessThanEqual(String status, LocalDateTime now);
}
