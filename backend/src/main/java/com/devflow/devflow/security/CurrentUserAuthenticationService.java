package com.devflow.devflow.security;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;

/** Resolves a verified token to the user's current database authorization state. */
@Service
public class CurrentUserAuthenticationService {
    private static final Set<String> VALID_ROLES = Set.of(
        "ADMIN", "PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER");

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public CurrentUserAuthenticationService(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase(Locale.ROOT);
        if (!VALID_ROLES.contains(role)) return null;

        return new UsernamePasswordAuthenticationToken(
            user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
