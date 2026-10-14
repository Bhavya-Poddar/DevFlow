package com.devflow.devflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devflow.devflow.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByEnabledTrue();

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<User> findByRole(String role);
}
