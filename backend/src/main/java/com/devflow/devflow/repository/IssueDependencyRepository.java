package com.devflow.devflow.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.IssueDependency;

public interface IssueDependencyRepository extends JpaRepository<IssueDependency, Long> {
    List<IssueDependency> findByBlockedIssue_Id(Long issueId);
    List<IssueDependency> findByBlockingIssue_Id(Long issueId);
    List<IssueDependency> findByBlockedIssue_IdIn(Collection<Long> issueIds);
    List<IssueDependency> findByBlockingIssue_IdIn(Collection<Long> issueIds);
    Optional<IssueDependency> findByBlockedIssue_IdAndBlockingIssue_Id(Long blockedIssueId, Long blockingIssueId);

    @Modifying
    @Query("delete from IssueDependency dependency where dependency.blockedIssue.id = :issueId "
        + "or dependency.blockingIssue.id = :issueId")
    int deleteAllForIssue(@Param("issueId") Long issueId);

    @Query("select blocked.id, blocked.title, blocked.status, project.id, project.name, "
        + "blocking.id, blocking.title, blocking.status, assignee.name "
        + "from IssueDependency dependency join dependency.blockedIssue blocked "
        + "join dependency.blockingIssue blocking left join blocked.project project "
        + "left join blocked.assignedUser assignee where blocking.status <> 'RESOLVED' "
        + "and (:projectId is null or project.id = :projectId) "
        + "and (:sprintId is null or blocked.sprint.id = :sprintId) order by blocked.id, blocking.id")
    List<Object[]> findDashboardBlockedTasks(@Param("projectId") Long projectId,
        @Param("sprintId") Long sprintId);
}
