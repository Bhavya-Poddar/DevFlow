package com.devflow.devflow.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "authorization_invariant_lock")
public class AuthorizationInvariantLock {
    @Id
    private Integer id;
    protected AuthorizationInvariantLock() {}
    public AuthorizationInvariantLock(Integer id) { this.id = id; }
}
