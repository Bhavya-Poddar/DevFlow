package com.devflow.devflow.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.IssueMaintenanceFollowUp;

public interface IssueMaintenanceFollowUpRepository extends JpaRepository<IssueMaintenanceFollowUp, Long> {
    List<IssueMaintenanceFollowUp> findByOriginalIssueIdOrderByReportedAtDesc(Long originalIssueId);
}
