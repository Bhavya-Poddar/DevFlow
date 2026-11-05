package com.devflow.devflow.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/** Tracks each transport session independently and closes it when its signed JWT expires or is revoked. */
@Component
public class JwtStompSessionLifecycle implements WebSocketHandlerDecoratorFactory {
    private static final Logger logger = LoggerFactory.getLogger(JwtStompSessionLifecycle.class);
    private static final Duration ACCOUNT_RECHECK_INTERVAL = Duration.ofSeconds(15);

    private final CurrentUserAuthenticationService authenticationService;
    private final JwtService jwtService;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ConcurrentMap<String, WebSocketSession> transports = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrackedSession> sessions = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> revocationSweep;

    @Autowired
    public JwtStompSessionLifecycle(CurrentUserAuthenticationService authenticationService,
            JwtService jwtService, TaskScheduler scheduler) {
        this(authenticationService, jwtService, scheduler, Clock.systemUTC());
    }

    JwtStompSessionLifecycle(CurrentUserAuthenticationService authenticationService,
            JwtService jwtService, TaskScheduler scheduler, Clock clock) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @PostConstruct
    void scheduleAccountRevocationChecks() {
        revocationSweep = scheduler.scheduleWithFixedDelay(this::revalidateSessions, ACCOUNT_RECHECK_INTERVAL);
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                onTransportEstablished(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                onTransportClosed(session.getId());
                super.afterConnectionClosed(session, status);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                invalidate(session.getId(), CloseStatus.SERVER_ERROR);
                super.handleTransportError(session, exception);
            }
        };
    }

    void onTransportEstablished(WebSocketSession session) {
        transports.put(session.getId(), session);
    }

    /** Called only after CONNECT's Bearer JWT has been cryptographically verified. */
    public boolean registerAuthenticatedSession(String sessionId, String token) {
        if (sessionId == null || token == null || token.isBlank() || !transports.containsKey(sessionId)) return false;
        final Instant expiresAt;
        try {
            expiresAt = jwtService.extractExpiration(token);
        } catch (RuntimeException invalidToken) {
            return false;
        }
        if (!expiresAt.isAfter(clock.instant())) return false;

        TrackedSession tracked = new TrackedSession(token, expiresAt);
        if (sessions.putIfAbsent(sessionId, tracked) != null) return false;
        try {
            tracked.expiryTask = scheduler.schedule(() -> invalidate(sessionId, CloseStatus.POLICY_VIOLATION), expiresAt);
            if (tracked.expiryTask == null) {
                invalidate(sessionId, CloseStatus.POLICY_VIOLATION);
                return false;
            }
            return true;
        } catch (RuntimeException schedulingFailure) {
            invalidate(sessionId, CloseStatus.SERVER_ERROR);
            logger.warn("Could not schedule expiry for an authenticated WebSocket session");
            return false;
        }
    }

    /** Revalidates the original signed token and current enabled account on each inbound frame. */
    public Authentication authenticateSession(String sessionId) {
        TrackedSession tracked = sessionId == null ? null : sessions.get(sessionId);
        if (tracked == null) return null;
        if (!tracked.expiresAt.isAfter(clock.instant())) {
            invalidate(sessionId, CloseStatus.POLICY_VIOLATION);
            return null;
        }
        Authentication authentication = authenticationService.authenticateToken(tracked.token);
        if (authentication == null) invalidate(sessionId, CloseStatus.POLICY_VIOLATION);
        return authentication;
    }

    /** Outbound guard ensures an expired/revoked socket cannot receive queued notifications. */
    public boolean allowOutbound(String sessionId) {
        return sessionId == null || authenticateSession(sessionId) != null;
    }

    public void disconnect(String sessionId) {
        invalidate(sessionId, CloseStatus.NORMAL);
    }

    public void rejectConnection(String sessionId) {
        invalidate(sessionId, CloseStatus.POLICY_VIOLATION);
    }

    void revalidateSessions() {
        sessions.keySet().forEach(this::authenticateSession);
    }

    void onTransportClosed(String sessionId) {
        removeAuthentication(sessionId);
        if (sessionId != null) transports.remove(sessionId);
    }

    private void invalidate(String sessionId, CloseStatus status) {
        removeAuthentication(sessionId);
        WebSocketSession transport = sessionId == null ? null : transports.remove(sessionId);
        if (transport != null && transport.isOpen()) {
            try {
                transport.close(status);
            } catch (IOException closeFailure) {
                logger.debug("Could not close an invalidated WebSocket transport");
            }
        }
    }

    private void removeAuthentication(String sessionId) {
        TrackedSession tracked = sessionId == null ? null : sessions.remove(sessionId);
        if (tracked != null && tracked.expiryTask != null) tracked.expiryTask.cancel(false);
    }

    @PreDestroy
    void shutdown() {
        if (revocationSweep != null) revocationSweep.cancel(false);
        sessions.keySet().forEach(id -> invalidate(id, CloseStatus.GOING_AWAY));
        transports.clear();
    }

    private static final class TrackedSession {
        private final String token;
        private final Instant expiresAt;
        private volatile ScheduledFuture<?> expiryTask;

        private TrackedSession(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
