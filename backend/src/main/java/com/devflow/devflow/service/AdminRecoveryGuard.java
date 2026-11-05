package com.devflow.devflow.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.repository.AuthorizationInvariantLockRepository;
import com.devflow.devflow.repository.UserRepository;

/** Serializes account/role operations that can affect availability of protected recovery access. */
@Service
public class AdminRecoveryGuard {
    private final AuthorizationInvariantLockRepository locks;
    private final UserRepository users;
    private final AdminRecoveryCapability recoveryCapability;

    public AdminRecoveryGuard(AuthorizationInvariantLockRepository locks, UserRepository users,
            AdminRecoveryCapability recoveryCapability) {
        this.locks = locks; this.users = users; this.recoveryCapability = recoveryCapability;
    }

    @Transactional
    public void lockInvariant() {
        if (locks.lockById(1) == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Administrative recovery invariant is not initialized");
        }
    }

    /** Use a locking/current read so MySQL REPEATABLE READ cannot validate an older snapshot. */
    @Transactional
    public void requireRecoveryAccount() {
        if (users.findEnabledRecoveryAccountsForUpdate().isEmpty()
                && !configuredRecoveryAccountRemains()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "At least one enabled recovery-capable account must remain");
        }
    }

    private boolean configuredRecoveryAccountRemains() {
        String email = recoveryCapability.configuredBootstrapEmail();
        return email != null && !email.isBlank()
            && users.findByEmailIgnoreCaseForUpdate(email)
                .filter(recoveryCapability::isConfiguredBootstrapAdmin).isPresent();
    }
}
