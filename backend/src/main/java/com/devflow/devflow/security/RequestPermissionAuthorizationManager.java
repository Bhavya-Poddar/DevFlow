package com.devflow.devflow.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import com.devflow.devflow.service.RequestPermissionService;

/** Resolves endpoint permission keys from the backend catalogue and applies resource scope. */
@Component
public class RequestPermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final RequestPermissionService requestPermissions;

    public RequestPermissionAuthorizationManager(RequestPermissionService requestPermissions) {
        this.requestPermissions = requestPermissions;
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken
                || current.getName() == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(requestPermissions.isAllowed(
            context.getRequest(), current.getName()));
    }
}
