package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.devflow.devflow.dto.*;
import com.devflow.devflow.entity.*;
import com.devflow.devflow.repository.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import com.devflow.devflow.dto.PendingApprovalItemDTO;
import com.devflow.devflow.dto.PendingApprovalsResponseDTO;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.service.NotificationService;

/** Serializes approval decisions by locking the task row; the unique cycle/stage key is a second guard. */
@Service
public class IssueApprovalService {
    private final IssueRepository issues;
    private final IssueApprovalRepository approvals;
    private final TestExecutionRepository executions;
    private final ProjectAccessService access;
    private final EffectivePermissionService permissions;
    private final AuditLogServices audit;
    private final AutomationEventService events;
    private final IssueMaintenanceService maintenance;
    private final ProjectRoleAssignmentRepository projectRoles;
    private final TeamRoleAssignmentRepository teamRoles;
    private final NotificationService notifications;

    public IssueApprovalService(IssueRepository issues, IssueApprovalRepository approvals, TestExecutionRepository executions,
            ProjectAccessService access, EffectivePermissionService permissions, AuditLogServices audit,
            AutomationEventService events, IssueMaintenanceService maintenance,
            ProjectRoleAssignmentRepository projectRoles, TeamRoleAssignmentRepository teamRoles,
            NotificationService notifications) {
        this.issues=issues;this.approvals=approvals;this.executions=executions;this.access=access;
        this.permissions=permissions;this.audit=audit;this.events=events;this.maintenance=maintenance;
        this.projectRoles=projectRoles;this.teamRoles=teamRoles;this.notifications=notifications;
    }

    @Transactional
    public IssueApprovalResponseDTO decide(Long issueId,String requestedStage,IssueApprovalRequestDTO request,String actorEmail) {
        Issue issue=issues.findByIdForUpdate(issueId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found"));
        access.requireProjectAccess(issue.getProject(),actorEmail);
        User actor=permissions.requireEnabledUser(actorEmail);
        String stage=requestedStage==null?"":requestedStage.trim().toUpperCase(Locale.ROOT);
        String expected= switch(stage){case "TEAM-LEAD","TEAM_LEAD"->IssueStatus.TEAM_LEAD_APPROVAL.name();case "PROJECT-MANAGER","PROJECT_MANAGER"->IssueStatus.PROJECT_MANAGER_APPROVAL.name();default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported approval stage");};
        String permission="TEAM_LEAD".equals(stage)||"TEAM-LEAD".equals(stage)?"ISSUE_APPROVE_TEAM_LEAD":"ISSUE_APPROVE_PROJECT_MANAGER";
        permissions.require(actorEmail,permission,projectId(issue),null);
        if(!expected.equals(issue.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Task is not awaiting this approval stage");
        if(!executions.existsByIssueIdAndWorkflowCycleAndResultAndStatus(issueId,issue.getWorkflowCycle(),"PASSED","COMPLETED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A passing test in the current workflow cycle is required");
        if("PROJECT_MANAGER".equals(stage)||"PROJECT-MANAGER".equals(stage)) {
            if(!approvals.existsByIssueIdAndWorkflowCycleAndStageAndDecision(issueId,issue.getWorkflowCycle(),"TEAM_LEAD","APPROVED"))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Team Lead approval in the current cycle is required first");
        }
        if(request==null||request.getDecision()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision is required");
        String decision=request.getDecision().trim().toUpperCase(Locale.ROOT);
        if(!List.of("APPROVED","REJECTED").contains(decision))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision must be APPROVED or REJECTED");
        String remark=request.getRemark()==null?null:request.getRemark().trim();
        if("REJECTED".equals(decision)&&(remark==null||remark.isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A remark is required when rejecting work");

        String approvalStage="TEAM-LEAD".equals(stage)?"TEAM_LEAD":"PROJECT_MANAGER".equals(stage)?"PROJECT_MANAGER":stage;
        IssueApproval approval=approvals.saveAndFlush(new IssueApproval(issue,issue.getWorkflowCycle(),approvalStage,decision,remark,actor,LocalDateTime.now()));
        String previous=issue.getStatus();
        if("REJECTED".equals(decision)) {
            issue.setStatus(IssueStatus.IN_PROGRESS.name());
            issue.setWorkflowCycle(issue.getWorkflowCycle()+1);
            audit.createLog("ISSUE_APPROVAL_REJECTED","targetType=ISSUE; targetId="+issueId+"; stage="+approvalStage+"; remark="+remark,issueId,actor.getId());
            notifyUser(issue, issue.getAssignedUser(), actor, "APPROVAL_REJECTED",
                "Task '" + issue.getTitle() + "' was rejected at " + approvalStage.replace('_',' ')
                    + " and returned to the developer. Remark: " + remark);
        } else if("TEAM_LEAD".equals(approvalStage)) {
            issue.setStatus(IssueStatus.PROJECT_MANAGER_APPROVAL.name());
            audit.createLog("ISSUE_APPROVAL_GRANTED","targetType=ISSUE; targetId="+issueId+"; stage=TEAM_LEAD",issueId,actor.getId());
            notifyApprovers(issue, actor, "ISSUE_APPROVE_PROJECT_MANAGER", "PROJECT_MANAGER_APPROVAL",
                "Team Lead approved this task. It is waiting for your final approval.");
        } else {
            issue.setStatus(IssueStatus.RESOLVED.name());
            maintenance.start(issue);
            audit.createLog("ISSUE_APPROVAL_GRANTED","targetType=ISSUE; targetId="+issueId+"; stage=PROJECT_MANAGER",issueId,actor.getId());
            events.enqueue(AutomationTriggerType.BLOCKING_TASK_RESOLVED,issue,previous,issue.getStatus(),"manager-approval:"+approval.getId());
            notifyUser(issue, issue.getAssignedUser(), actor, "APPROVAL_RESOLVED",
                "Final approval completed. Task '" + issue.getTitle() + "' is resolved.");
            notifyUser(issue, issue.getTester(), actor, "APPROVAL_RESOLVED",
                "Final approval completed. Task '" + issue.getTitle() + "' is resolved.");
        }
        issues.save(issue);
        if(!previous.equals(issue.getStatus())) events.enqueue(AutomationTriggerType.TASK_STATUS_CHANGED,issue,previous,issue.getStatus(),"approval:"+approval.getId());
        return new IssueApprovalResponseDTO(approval);
    }

    @Transactional(readOnly=true)
    public List<IssueApprovalResponseDTO> history(Long issueId,String actorEmail) {
        access.requireIssueAccess(issueId,actorEmail);
        return approvals.findByIssueIdOrderByWorkflowCycleAscDecidedAtAscIdAsc(issueId).stream().map(IssueApprovalResponseDTO::new).toList();
    }

    @Transactional(readOnly=true)
    public PendingApprovalsResponseDTO pendingFor(String actorEmail) {
        permissions.requireEnabledUser(actorEmail);
        List<Issue> candidates=issues.findByStatusInOrderByUpdatedAtAscIdAsc(List.of(
            IssueStatus.TEAM_LEAD_APPROVAL.name(), IssueStatus.PROJECT_MANAGER_APPROVAL.name()));
        LocalDateTime now=LocalDateTime.now();
        List<PendingApprovalItemDTO> pending=new ArrayList<>();
        for(Issue issue:candidates) {
            Long projectId=projectId(issue);
            if(projectId==null || issue.getProject()==null) continue;
            String status=issue.getStatus();
            boolean teamLeadStage=IssueStatus.TEAM_LEAD_APPROVAL.name().equals(status);
            String permission=teamLeadStage?"ISSUE_APPROVE_TEAM_LEAD":"ISSUE_APPROVE_PROJECT_MANAGER";
            if(!permissions.has(actorEmail,permission,projectId,null)) continue;
            TestExecution test=executions.findFirstByIssueIdAndWorkflowCycleAndResultAndStatusOrderByCompletedAtDescIdDesc(
                issue.getId(),issue.getWorkflowCycle(),"PASSED","COMPLETED").orElse(null);
            if(test==null) continue;
            IssueApproval leadApproval=teamLeadStage?null:approvals
                .findFirstByIssueIdAndWorkflowCycleAndStageAndDecisionOrderByDecidedAtDescIdDesc(
                    issue.getId(),issue.getWorkflowCycle(),"TEAM_LEAD","APPROVED").orElse(null);
            LocalDateTime waitingSince=teamLeadStage?test.getCompletedAt():leadApproval==null?null:leadApproval.getDecidedAt();
            if(waitingSince==null) continue;
            pending.add(new PendingApprovalItemDTO(issue.getId(),issue.getTitle(),projectId,issue.getProject().getName(),
                issue.getAssignedUser()==null?null:issue.getAssignedUser().getName(),
                issue.getTester()==null?null:issue.getTester().getName(),
                teamLeadStage?"TEAM_LEAD":"PROJECT_MANAGER",test.getResult(),test.getCompletedAt(),
                leadApproval==null?null:leadApproval.getActor().getName(),
                leadApproval==null?null:leadApproval.getDecidedAt(),waitingSince,
                Math.max(0,Duration.between(waitingSince,now).getSeconds())));
        }
        pending.sort(Comparator.comparing(PendingApprovalItemDTO::waitingSince));
        long leadCount=pending.stream().filter(item->"TEAM_LEAD".equals(item.stage())).count();
        long managerCount=pending.stream().filter(item->"PROJECT_MANAGER".equals(item.stage())).count();
        return new PendingApprovalsResponseDTO(leadCount,managerCount,pending);
    }

    private void notifyApprovers(Issue issue, User actor, String permission,String type,String message) {
        Long projectId=projectId(issue);
        if(projectId==null) return;
        Map<Long,User> recipients=new LinkedHashMap<>();
        for(ProjectRoleAssignment assignment:projectRoles.findByProjectId(projectId)) {
            User user=assignment.getUser();
            if(user.isEnabled() && permissions.has(user.getEmail(),permission,projectId,null)) recipients.put(user.getId(),user);
        }
        if(issue.getProject().getTeam()!=null) {
            for(TeamRoleAssignment assignment:teamRoles.findByTeamId(issue.getProject().getTeam().getId())) {
                User user=assignment.getUser();
                if(user.isEnabled() && permissions.has(user.getEmail(),permission,projectId,null)) recipients.put(user.getId(),user);
            }
        }
        recipients.values().forEach(user->notifyUser(issue,user,actor,type,message));
    }

    private void notifyUser(Issue issue, User user, User actor, String type,String message) {
        if(user!=null && user.isEnabled() && (actor == null || !user.getId().equals(actor.getId())))
            notifications.createNotification(user,type,message,NotificationTargetPaths.issue(issue.getId()));
    }
    private Long projectId(Issue issue){return issue.getProject()==null?null:issue.getProject().getId();}
}
