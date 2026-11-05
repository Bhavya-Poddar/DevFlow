package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.devflow.devflow.dto.*;
import com.devflow.devflow.entity.*;
import com.devflow.devflow.repository.*;

@Service
public class IssueMaintenanceService {
    private final IssueMaintenanceRepository maintenance;
    private final IssueMaintenanceFollowUpRepository followUps;
    private final AdminWebsiteSettingRepository settings;
    private final IssueRepository issues;
    private final UserRepository users;
    private final ProjectAccessService access;
    private final EffectivePermissionService permissions;
    private final AuditLogServices audit;
    @org.springframework.beans.factory.annotation.Autowired private NotificationService notifications;

    public IssueMaintenanceService(IssueMaintenanceRepository maintenance, IssueMaintenanceFollowUpRepository followUps,
            AdminWebsiteSettingRepository settings, IssueRepository issues, UserRepository users,
            ProjectAccessService access, EffectivePermissionService permissions, AuditLogServices audit) {
        this.maintenance=maintenance;this.followUps=followUps;this.settings=settings;this.issues=issues;this.users=users;
        this.access=access;this.permissions=permissions;this.audit=audit;
    }

    @Transactional
    public IssueMaintenance start(Issue issue) {
        int days=periodDays();
        LocalDateTime now=LocalDateTime.now();
        return maintenance.save(new IssueMaintenance(issue,now,now.plusDays(days)));
    }

    @Transactional(readOnly=true)
    public IssueMaintenanceResponseDTO get(Long issueId, String actorEmail) {
        access.requireIssueAccess(issueId,actorEmail);
        IssueMaintenance record=maintenance.findByIssueId(issueId).orElse(null);
        if(record==null)return null; // Legacy resolved tasks are not assigned fabricated maintenance history.
        List<IssueMaintenanceResponseDTO.FollowUp> entries=followUps.findByOriginalIssueIdOrderByReportedAtDesc(issueId).stream()
            .map(f->new IssueMaintenanceResponseDTO.FollowUp(f.getFollowUpIssue().getId(),f.getFollowUpIssue().getTitle(),
                f.getReason(),new CollaboratorResponseDTO(f.getReporter()),new CollaboratorResponseDTO(f.getResponsibleUser()),f.getReportedAt())).toList();
        return new IssueMaintenanceResponseDTO(issueId,record.getStatus(),record.getStartedAt(),record.getExpiresAt(),record.getCompletedAt(),entries);
    }

    @Transactional
    public IssueMaintenanceResponseDTO.FollowUp createFollowUp(Long originalId, MaintenanceFollowUpRequestDTO request,String actorEmail) {
        Issue original=issues.findByIdForUpdate(originalId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found"));
        access.requireProjectAccess(original.getProject(),actorEmail);
        permissions.require(actorEmail,"ISSUE_CREATE",projectId(original),null);
        if(request==null||request.getResponsibleDeveloperId()==null||request.getReason()==null||request.getReason().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A responsible developer and maintenance reason are required");
        String title=request.getTitle()==null||request.getTitle().isBlank()?"Maintenance follow-up: "+original.getTitle():request.getTitle().trim();
        if(title.length()>255)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Follow-up title must be at most 255 characters");
        IssueMaintenance window=maintenance.findByIssueId(originalId).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"This task has no recorded maintenance window"));
        LocalDateTime now=LocalDateTime.now();
        if(!"ACTIVE".equals(window.getStatus())||!window.getExpiresAt().isAfter(now))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"The maintenance period has expired");
        User reporter=permissions.requireEnabledUser(actorEmail);
        User responsible=users.findById(request.getResponsibleDeveloperId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Responsible developer not found"));
        if(!responsible.isEnabled())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Responsible developer is disabled");
        if(original.getProject()!=null&&original.getProject().getTeam()!=null) {
            if(!access.isMember(original.getProject().getTeam(),responsible.getId())||!permissions.hasProjectRole(responsible.getEmail(),projectId(original),"DEVELOPER_PROJECT")
                    ||!permissions.hasTeamRole(responsible.getEmail(),original.getProject().getTeam().getId(),"DEVELOPER_TEAM"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Responsible developer must have a developer responsibility in this project team");
        } else if(!permissions.hasGlobal(responsible.getEmail(),"ASSIGNABLE_DEVELOPER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Responsible user is not assignable as a developer");
        }
        Issue followUp=new Issue();followUp.setTitle(title);followUp.setDescription("Follow-up to task #"+originalId+"\n\n"+request.getReason().trim());
        followUp.setPriority(original.getPriority());followUp.setStatus(IssueStatus.OPEN.name());followUp.setProject(original.getProject());
        followUp.setAssignedUser(responsible);followUp.setReportedBy(reporter);
        followUp=issues.saveAndFlush(followUp);
        IssueMaintenanceFollowUp link=followUps.save(new IssueMaintenanceFollowUp(original,followUp,reporter,responsible,request.getReason().trim(),now));
        audit.createLog("MAINTENANCE_FOLLOW_UP_CREATED","targetType=ISSUE; targetId="+originalId+"; followUpIssueId="+followUp.getId()+"; responsibleUserId="+responsible.getId(),originalId,reporter.getId());
        audit.createLog("ISSUE_CREATED","targetType=ISSUE; targetId="+followUp.getId()+"; maintenance follow-up for="+originalId,followUp.getId(),reporter.getId());
        if (notifications != null && !responsible.getId().equals(reporter.getId())) notifications.createNotification(
            responsible, "MAINTENANCE_FOLLOW_UP_ASSIGNED",
            "You were assigned a maintenance follow-up for task '" + original.getTitle() + "'.", NotificationTargetPaths.issue(followUp.getId()));
        return new IssueMaintenanceResponseDTO.FollowUp(followUp.getId(),followUp.getTitle(),link.getReason(),new CollaboratorResponseDTO(reporter),new CollaboratorResponseDTO(responsible),link.getReportedAt());
    }

    @Transactional
    public int completeExpired(LocalDateTime now) {
        List<IssueMaintenance> expired=maintenance.findByStatusAndExpiresAtLessThanEqual("ACTIVE",now);
        expired.forEach(record->{record.setStatus("COMPLETED");record.setCompletedAt(now);});
        return expired.size();
    }

    private int periodDays(){
        String value=settings.findById("maintenance.period.days").map(AdminWebsiteSetting::getValue).orElse("30");
        try {int days=Integer.parseInt(value);if(days<1||days>365)throw new NumberFormatException();return days;}
        catch(NumberFormatException e){throw new IllegalStateException("Configured maintenance period must be between 1 and 365 days");}
    }
    private Long projectId(Issue issue){return issue.getProject()==null?null:issue.getProject().getId();}
}
