package com.devflow.devflow.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.GitHubIssueLink;

public interface GitHubIssueLinkRepository extends JpaRepository<GitHubIssueLink, Long> {
    Optional<GitHubIssueLink> findByIssue_Id(Long issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from GitHubIssueLink link where link.id = :linkId")
    Optional<GitHubIssueLink> findByIdForUpdate(@Param("linkId") Long linkId);
}
