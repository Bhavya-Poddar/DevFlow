package com.devflow.devflow.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.EffectivePermissionService;

/** Resolves a verified token to the user's current database authorization state. */
@Service
public class CurrentUserAuthenticationService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final EffectivePermissionService permissions;

    public CurrentUserAuthenticationService(JwtService jwtService, UserRepository userRepository,
            EffectivePermissionService permissions) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.permissions = permissions;
    }

    /** Returns null for invalid tokens, deleted/disabled accounts, or invalid stored roles. */
    public Authentication authenticateToken(String token) {
        if (token == null || !jwtService.isTokenValid(token)) return null;
        return authenticateEmail(jwtService.extractEmail(token));
    }

    /** Rechecks an established REST/WebSocket identity against current account state. */
    public Authentication authenticateEmail(String email) {
        if (email == null || email.isBlank()) return null;

        Optional<User> currentUser = userRepository.findByEmailIgnoreCase(email);
        if (currentUser.isEmpty() || !currentUser.get().isEnabled()) return null;

        User user = currentUser.get();
        try {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            permissions.globalPermissions(user.getEmail()).stream()
                .map(key -> new SimpleGrantedAuthority("PERM_" + key)).forEach(authorities::add);
            if (permissions.hasRecoveryCapability(user.getEmail())) {
                authorities.add(new SimpleGrantedAuthority("RECOVERY_CAPABLE"));
            }
            // Keep the legacy authority for non-security integrations during the compatibility rollout.
            if (user.getRole() != null && !user.getRole().isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().trim().toUpperCase(java.util.Locale.ROOT)));
            }
            if (authorities.isEmpty()) return null;
        return new UsernamePasswordAuthenticationToken(
            user.getEmail(), null, authorities);
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            return null;
        }
    }
}
