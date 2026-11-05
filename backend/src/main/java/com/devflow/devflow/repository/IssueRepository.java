package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;

import com.devflow.devflow.entity.Issue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface IssueRepository extends JpaRepository<Issue, Long> {

    long countByPriority(String priority);
    long countByStatus(String status);

    List<Issue> findByProjectId(Long projectId);

    Optional<Issue> findByIdAndProjectId(Long id, Long projectId);

    List<Issue> findBySprintId(Long sprintId);

    @Query("select count(issue), sum(case when issue.status in ('RESOLVED', 'CLOSED') then 0 else 1 end) "
        + "from Issue issue where issue.sprint.id = :sprintId")
    List<Object[]> countSprintBurndownIssues(@Param("sprintId") Long sprintId);

    List<Issue> findByStatusAndTesterIsNotNullOrderByUpdatedAtAsc(String status);

    List<Issue> findByStatusInOrderByUpdatedAtAscIdAsc(Collection<String> statuses);

    List<Issue> findByTesterIdAndStatusInOrderByUpdatedAtAsc(Long testerId, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select issue from Issue issue where issue.id = :issueId")
    Optional<Issue> findByIdForUpdate(@Param("issueId") Long issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select issue from Issue issue where issue.id in :issueIds order by issue.id")
    List<Issue> findAllByIdForUpdate(@Param("issueIds") Collection<Long> issueIds);

    @Query("select issue.status, count(issue) from Issue issue group by issue.status")
    List<Object[]> dashboardStatusCounts();

    @Query("select project.id, project.name, count(issue), "
        + "sum(case when issue.status = 'RESOLVED' then 1 else 0 end) "
        + "from Project project left join Issue issue on issue.project = project "
        + "group by project.id, project.name order by project.name")
    List<Object[]> dashboardProjectProgress();

    @Query("select user.id, user.name, count(issue), user.avatarKey from Issue issue join issue.assignedUser user "
        + "where user.role = 'DEVELOPER' group by user.id, user.name")
    List<Object[]> dashboardDeveloperWorkload();

    @Query("select user.id, user.name, count(issue), user.avatarKey from Issue issue join issue.tester user "
        + "where user.role = 'TESTER' group by user.id, user.name")
    List<Object[]> dashboardTesterWorkload();

    @Query("select issue.status, count(issue) from Issue issue "
        + "where (:projectId is null or issue.project.id = :projectId) "
        + "and (:sprintId is null or issue.sprint.id = :sprintId) group by issue.status")
    List<Object[]> dashboardStatusCounts(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select project.id, project.name, count(issue), "
        + "sum(case when issue.status = 'RESOLVED' then 1 else 0 end) "
        + "from Project project left join Issue issue on issue.project = project "
        + "and (:sprintId is null or issue.sprint.id = :sprintId) "
        + "where (:projectId is null or project.id = :projectId) "
        + "group by project.id, project.name order by project.name")
    List<Object[]> dashboardProjectProgress(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select user.id, user.name, count(issue), user.avatarKey from Issue issue join issue.assignedUser user "
        + "where user.role = 'DEVELOPER' and (:projectId is null or issue.project.id = :projectId) "
        + "and (:sprintId is null or issue.sprint.id = :sprintId) "
        + "group by user.id, user.name, user.avatarKey")
    List<Object[]> dashboardDeveloperWorkload(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select user.id, user.name, count(issue), user.avatarKey from Issue issue join issue.tester user "
        + "where user.role = 'TESTER' and (:projectId is null or issue.project.id = :projectId) "
        + "and (:sprintId is null or issue.sprint.id = :sprintId) "
        + "group by user.id, user.name, user.avatarKey")
    List<Object[]> dashboardTesterWorkload(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select count(issue) from Issue issue join issue.sprint sprint "
        + "where issue.status <> 'RESOLVED' and sprint.endDate < :today "
        + "and (:projectId is null or issue.project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId)")
    long countUnresolvedIssuesInEndedSprints(@Param("today") LocalDate today,
        @Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select issue.id, issue.title, issue.status, project.id, project.name, sprint.id, sprint.name, sprint.endDate, user.name "
        + "from Issue issue join issue.sprint sprint left join issue.project project "
        + "left join issue.assignedUser user where issue.status <> 'RESOLVED' and sprint.endDate < :today "
        + "and (:projectId is null or project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId) order by sprint.endDate, issue.id")
    List<Object[]> findUnresolvedIssuesInEndedSprints(@Param("today") LocalDate today,
        @Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select issue.id, issue.title, issue.status, issue.dueDate, project.id, project.name, "
        + "sprint.id, sprint.name, user.name from Issue issue left join issue.project project "
        + "left join issue.sprint sprint left join issue.assignedUser user "
        + "where issue.status <> 'RESOLVED' and issue.dueDate < :today "
        + "and (:projectId is null or project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId) order by issue.dueDate, issue.id")
    List<Object[]> findDashboardOverdueTasks(@Param("today") LocalDate today,
        @Param("projectId") Long projectId, @Param("sprintId") Long sprintId, Pageable pageable);

    @Query("select issue.id, issue.title, issue.status, issue.dueDate, project.id, project.name, "
        + "sprint.id, sprint.name, user.name from Issue issue left join issue.project project "
        + "left join issue.sprint sprint left join issue.assignedUser user "
        + "where issue.status <> 'RESOLVED' and issue.dueDate >= :today and issue.dueDate <= :throughDate "
        + "and (:projectId is null or project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId) order by issue.dueDate, issue.id")
    List<Object[]> findDashboardApproachingDeadlineTasks(@Param("today") LocalDate today,
        @Param("throughDate") LocalDate throughDate, @Param("projectId") Long projectId,
        @Param("sprintId") Long sprintId, Pageable pageable);

    @Query("select user.id, user.name, user.avatarKey, count(issue) from Issue issue "
        + "left join issue.assignedUser user where issue.status <> 'RESOLVED' "
        + "and (:projectId is null or issue.project.id = :projectId) "
        + "and (:sprintId is null or issue.sprint.id = :sprintId) "
        + "group by user.id, user.name, user.avatarKey order by count(issue) desc, user.name")
    List<Object[]> dashboardUnresolvedWorkloadByAssignee(@Param("projectId") Long projectId,
        @Param("sprintId") Long sprintId);

}
