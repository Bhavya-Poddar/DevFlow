package com.devflow.devflow.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.devflow.devflow.exception.ApiErrorWriter;

@Configuration
@EnableWebSecurity
@Import(com.devflow.devflow.exception.ApiErrorWriter.class)
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${devflow.api-docs.public-access:false}")
    private boolean apiDocsPublicAccess;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter apiErrorWriter,
            RequestPermissionAuthorizationManager permissionAuthorizationManager) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, denied) ->
                    apiErrorWriter.write(request, response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED", "Authentication is required or invalid."))
                .accessDeniedHandler((request, response, denied) -> {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (HttpMethod.PUT.matches(request.getMethod())
                        && ("/api/issues".equals(path) || path.startsWith("/api/issues/"))) {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    logger.warn("Security denied issue update: method={} path={} authorities={} rejection={}",
                        request.getMethod(), path,
                        authentication == null ? "none" : authentication.getAuthorities(),
                        denied.getClass().getSimpleName());
                }
                if (HttpMethod.DELETE.matches(request.getMethod())
                        && ("/api/issues".equals(path) || path.startsWith("/api/issues/"))) {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    logger.warn("Security denied issue deletion: method={} path={} authorities={}",
                        request.getMethod(), path,
                        authentication == null ? "none" : authentication.getAuthorities());
                }
                apiErrorWriter.write(request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                        "FORBIDDEN", "You are not allowed to perform this action.");
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,
                        "/actuator/health/liveness",
                        "/actuator/health/readiness").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml")
                    .access((authentication, context) -> {
                        Authentication current = authentication.get();
                        boolean admin = current != null && current.isAuthenticated()
                                && current.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        return new AuthorizationDecision(apiDocsPublicAccess || admin);
                    })
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                // The STOMP CONNECT frame is authenticated with the existing JWT.
                .requestMatchers("/ws", "/ws/**").permitAll()

                .anyRequest().access(permissionAuthorizationManager)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
