package com.devflow.devflow.service;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class IssueMaintenanceScheduler {
    private final IssueMaintenanceService service;
    public IssueMaintenanceScheduler(IssueMaintenanceService service){this.service=service;}
    @Scheduled(cron="0 15 0 * * *",zone="UTC")
    public void completeExpiredMaintenance(){service.completeExpired(LocalDateTime.now());}
}
