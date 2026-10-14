package com.devflow.devflow.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, denied) -> {
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
                new AccessDeniedHandlerImpl().handle(request, response, denied);
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // The STOMP CONNECT frame is authenticated with the existing JWT.
                .requestMatchers("/ws", "/ws/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/users/me/profile", "/api/users/me/password").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/users/me/avatar").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users/*/avatar").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users/assignable").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users/testers").authenticated()
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.DELETE, "/api/audit-logs/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET,
                        "/api/projects", "/api/projects/**",
                        "/api/issues", "/api/issues/**",
                        "/api/sprints", "/api/sprints/**",
                        "/api/comments", "/api/comments/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER")

                .requestMatchers(HttpMethod.GET, "/api/audit-logs/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")

                .requestMatchers(HttpMethod.GET, "/api/reports/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER")

                .requestMatchers(HttpMethod.GET, "/api/dashboard/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER")

                .requestMatchers(HttpMethod.GET, "/api/testing/queue")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "TESTER")
                .requestMatchers(HttpMethod.GET, "/api/testing/my")
                    .hasRole("TESTER")
                .requestMatchers(HttpMethod.GET, "/api/testing/issue/*/history")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "TESTER", "VIEWER")
                .requestMatchers(HttpMethod.POST, "/api/testing/*/start", "/api/testing/*/complete")
                    .hasAnyRole("TESTER", "ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/projects", "/api/projects/**",
                        "/api/sprints", "/api/sprints/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/projects", "/api/projects/**",
                        "/api/sprints", "/api/sprints/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/projects", "/api/projects/**",
                        "/api/sprints", "/api/sprints/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")

                .requestMatchers(HttpMethod.POST, "/api/issues", "/api/issues/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER")
                .requestMatchers(HttpMethod.PUT, "/api/issues/*/tester")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/issues", "/api/issues/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER")
                .requestMatchers(HttpMethod.DELETE, "/api/issues", "/api/issues/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")

                .requestMatchers(HttpMethod.POST, "/api/comments", "/api/comments/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER", "DEVELOPER")
                .requestMatchers(HttpMethod.DELETE, "/api/comments", "/api/comments/**")
                    .hasAnyRole("ADMIN", "PROJECT_MANAGER")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
