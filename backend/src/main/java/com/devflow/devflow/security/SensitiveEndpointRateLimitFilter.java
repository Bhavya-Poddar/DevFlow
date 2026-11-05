package com.devflow.devflow.security;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.devflow.devflow.exception.ApiErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Applies IP-based limits before login or webhook request processing. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_BUCKET = "login:";
    private static final String LOGIN_PATH = "/api/auth/login";

    private final IpRateLimiter limiter;
    private final ApiErrorWriter errorWriter;
    private final int loginMaximum;
    private final Duration loginWindow;

    public SensitiveEndpointRateLimitFilter(
            ApiErrorWriter errorWriter,
            @Value("${devflow.rate-limit.max-tracked-keys:100000}") int maxTrackedKeys,
            @Value("${devflow.rate-limit.login.max-requests:10}") int loginMaximum,
            @Value("${devflow.rate-limit.login.window:15m}") Duration loginWindow) {
        this.limiter = new IpRateLimiter(maxTrackedKeys);
        this.errorWriter = errorWriter;
        validate(loginMaximum, loginWindow, "login");
        this.loginMaximum = loginMaximum;
        this.loginWindow = loginWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod()) || !LOGIN_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Use only the network peer address. Forwarded headers are untrusted and ignored.
        String peerAddress = request.getRemoteAddr();
        if (peerAddress == null || peerAddress.isBlank()) peerAddress = "unknown-peer";
        long retryAfter = limiter.acquire(
                LOGIN_BUCKET + peerAddress,
                loginMaximum,
                loginWindow,
                System.currentTimeMillis());

        if (retryAfter > 0) {
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
            response.setHeader("Retry-After", Long.toString(retryAfter));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void validate(int maximum, Duration window, String endpoint) {
        if (maximum < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit for " + endpoint + " must have a positive count and window");
        }
    }
}
