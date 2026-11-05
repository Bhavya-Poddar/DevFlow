package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.devflow.devflow.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long>{

    boolean existsByTeam_Id(Long teamId);
    java.util.Optional<Project> findByTeam_Id(Long teamId);

    long countByCategory(String category);
    long countByStatus(String status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "team", "team.manager" })
    @Query("select distinct p from Project p where p.team is null or p.team.manager.id = :userId "
            + "or exists (select member.id from p.team.members member where member.id = :userId) order by p.name")
    java.util.List<Project> findVisibleToUser(@Param("userId") Long userId);

    @Query("select case when p.team is null then true else "
            + "(p.team.manager.id = :userId or exists "
            + "(select member.id from p.team.members member where member.id = :userId)) end "
            + "from Project p where p.id = :projectId")
    java.util.Optional<Boolean> isVisibleToUser(@Param("projectId") Long projectId, @Param("userId") Long userId);

    boolean existsByGithubRepositoryOwnerIgnoreCaseAndGithubRepositoryNameIgnoreCase(String owner, String name);

    boolean existsByGithubRepositoryOwnerIgnoreCaseAndGithubRepositoryNameIgnoreCaseAndIdNot(
            String owner, String name, Long id);

    java.util.Optional<Project> findByGithubRepositoryOwnerIgnoreCaseAndGithubRepositoryNameIgnoreCase(
            String owner, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where lower(p.githubRepositoryOwner) = lower(:owner) "
            + "and lower(p.githubRepositoryName) = lower(:name)")
    java.util.Optional<Project> findByGithubRepositoryForUpdate(@Param("owner") String owner,
            @Param("name") String name);

}
