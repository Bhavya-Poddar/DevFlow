package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.GitHubPullRequest;

import jakarta.persistence.LockModeType;

public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pr from GitHubPullRequest pr where pr.repositoryOwner = :owner "
        + "and pr.repositoryName = :repository and pr.pullRequestNumber = :number")
    Optional<GitHubPullRequest> findForUpdate(@Param("owner") String owner,
        @Param("repository") String repository, @Param("number") Long number);

    @EntityGraph(attributePaths = { "project" })
    List<GitHubPullRequest> findDistinctByLinkedIssues_IdOrderByUpdatedAtDescIdDesc(Long issueId);
}
