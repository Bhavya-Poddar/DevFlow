package com.devflow.devflow.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.devflow.devflow.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByRole(String role);
    long countByEnabledTrue();
    long countByEnabledFalse();
    long countByEnabledTrueAndAdminRecoveryTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.enabled = true and u.adminRecovery = true order by u.id")
    java.util.List<User> findEnabledRecoveryAccountsForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<User> findByRole(String role);
    java.util.List<User> findByRoleInAndEnabledTrue(java.util.Collection<String> roles);
}
