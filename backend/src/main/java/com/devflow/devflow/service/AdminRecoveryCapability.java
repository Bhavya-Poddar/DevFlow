package com.devflow.devflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.util.EmailAddress;

/** Resolves the protected recovery capability independently of editable permission grants. */
@Component
public class AdminRecoveryCapability {
    private final String bootstrapAdminEmail;

    public AdminRecoveryCapability(@Value("${devflow.bootstrap.admin.email:}") String bootstrapAdminEmail) {
        this.bootstrapAdminEmail = EmailAddress.normalize(bootstrapAdminEmail);
    }

    public boolean hasCapability(User user) {
        return user != null && user.isEnabled()
            && (user.hasAdminRecovery() || isConfiguredBootstrapAdmin(user));
    }

    public boolean isConfiguredBootstrapAdmin(User user) {
        return user != null && user.isEnabled()
            && "ADMIN".equalsIgnoreCase(user.getRole())
            && bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank()
            && bootstrapAdminEmail.equals(EmailAddress.normalize(user.getEmail()));
    }

    public String configuredBootstrapEmail() {
        return bootstrapAdminEmail;
    }
}
