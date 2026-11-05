package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = { "manager", "members" })
    @Query("select distinct t from Team t where t.id = :id")
    Optional<Team> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = { "manager", "members" })
    @Query("select distinct t from Team t order by t.name")
    List<Team> findAllDetailed();

    @EntityGraph(attributePaths = { "manager", "members" })
    @Query("select distinct t from Team t where t.manager.id = :userId or exists "
        + "(select member.id from t.members member where member.id = :userId) order by t.name")
    List<Team> findVisibleToUser(@Param("userId") Long userId);

    @EntityGraph(attributePaths = { "manager", "members" })
    @Query("select distinct t from Team t where t.manager.id = :userId order by t.name")
    List<Team> findManagedByUser(@Param("userId") Long userId);
}
