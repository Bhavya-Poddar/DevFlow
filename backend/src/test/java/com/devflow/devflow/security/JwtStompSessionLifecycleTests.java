package com.devflow.devflow.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class JwtStompSessionLifecycleTests {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private CurrentUserAuthenticationService authenticationService;
    private JwtService jwtService;
    private TaskScheduler scheduler;
    private JwtStompSessionLifecycle lifecycle;
    private Authentication alice;
    private WebSocketSession firstSocket;
    private ScheduledFuture<?> expiryFuture;

    @BeforeEach
    void setUp() {
        authenticationService = mock(CurrentUserAuthenticationService.class);
        jwtService = mock(JwtService.class);
        scheduler = mock(TaskScheduler.class);
        expiryFuture = mock(ScheduledFuture.class);
        doReturn(expiryFuture).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        lifecycle = new JwtStompSessionLifecycle(authenticationService, jwtService, scheduler, clock);
        alice = new UsernamePasswordAuthenticationToken("alice@example.com", null, List.of());
        firstSocket = socket("session-a");
        lifecycle.onTransportEstablished(firstSocket);
    }

    @Test
    void validJwtIsTrackedPerConnectionUntilItsSignedExpiry() {
        Instant expiry = now.get().plus(Duration.ofMinutes(20));
        when(jwtService.extractExpiration("jwt-a")).thenReturn(expiry);
        when(authenticationService.authenticateToken("jwt-a")).thenReturn(alice);

        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-a"));
        assertSame(alice, lifecycle.authenticateSession("session-a"));
        verify(scheduler).schedule(any(Runnable.class), eq(expiry));
    }

    @Test
    void alreadyExpiredJwtCannotConnect() {
        when(jwtService.extractExpiration("expired")).thenReturn(now.get().minusMillis(1));

        assertFalse(lifecycle.registerAuthenticatedSession("session-a", "expired"));
        assertNull(lifecycle.authenticateSession("session-a"));
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void jwtExpiryClosesOnlyTheEstablishedTransportAndCancelsItsExpiryTask() throws Exception {
        Instant expiry = now.get().plusSeconds(5);
        when(jwtService.extractExpiration("jwt-a")).thenReturn(expiry);
        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-a"));
        now.set(expiry);

        assertNull(lifecycle.authenticateSession("session-a"));

        verify(firstSocket).close(CloseStatus.POLICY_VIOLATION);
        verify(expiryFuture).cancel(false);
        assertFalse(lifecycle.allowOutbound("session-a"));
    }

    @Test
    void clientFrameAfterExpiryIsRejectedAndClosesTheSocket() throws Exception {
        Instant expiry = now.get().plusSeconds(2);
        when(jwtService.extractExpiration("jwt-a")).thenReturn(expiry);
        when(authenticationService.authenticateToken("jwt-a")).thenReturn(alice);
        JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(authenticationService, lifecycle);
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionId("session-a");
        connect.setNativeHeader("Authorization", "Bearer jwt-a");
        connect.setLeaveMutable(true);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders());
        assertNotNull(interceptor.preSend(connectMessage, null));

        now.set(expiry.plusMillis(1));
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setSessionId("session-a");
        subscribe.setDestination("/user/queue/notifications");
        subscribe.setUser(alice);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders());

        assertNull(interceptor.preSend(message, null));
        verify(firstSocket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void outboundNotificationIsBlockedWhenJwtExpiresWithoutAnotherClientFrame() throws Exception {
        Instant expiry = now.get().plusSeconds(3);
        when(jwtService.extractExpiration("jwt-a")).thenReturn(expiry);
        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-a"));
        now.set(expiry.plusMillis(1));

        assertFalse(lifecycle.allowOutbound("session-a"));
        verify(firstSocket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void accountDeactivationRevokesAndClosesItsSessionOnTheServerSweep() throws Exception {
        Instant expiry = now.get().plus(Duration.ofMinutes(10));
        when(jwtService.extractExpiration("jwt-a")).thenReturn(expiry);
        when(authenticationService.authenticateToken("jwt-a")).thenReturn(alice, (Authentication) null);
        assertSame(alice, authenticationService.authenticateToken("jwt-a"));
        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-a"));

        lifecycle.revalidateSessions();

        verify(firstSocket).close(CloseStatus.POLICY_VIOLATION);
        assertNull(lifecycle.authenticateSession("session-a"));
    }

    @Test
    void sessionsForOneUserExpireIndependently() throws Exception {
        WebSocketSession secondSocket = socket("session-b");
        lifecycle.onTransportEstablished(secondSocket);
        Instant firstExpiry = now.get().plusSeconds(5);
        when(jwtService.extractExpiration("jwt-a")).thenReturn(firstExpiry);
        when(jwtService.extractExpiration("jwt-b")).thenReturn(firstExpiry.plusSeconds(30));
        when(authenticationService.authenticateToken("jwt-b")).thenReturn(alice);
        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-a"));
        assertTrue(lifecycle.registerAuthenticatedSession("session-b", "jwt-b"));
        now.set(firstExpiry);

        lifecycle.revalidateSessions();

        verify(firstSocket).close(CloseStatus.POLICY_VIOLATION);
        verify(secondSocket, never()).close(any(CloseStatus.class));
        assertSame(alice, lifecycle.authenticateSession("session-b"));
    }

    @Test
    void disconnectCleansUpOnlyThatSessionsState() throws Exception {
        Instant expiry = now.get().plus(Duration.ofMinutes(1));
        when(jwtService.extractExpiration("jwt-a")).thenReturn(expiry);
        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-a"));

        lifecycle.disconnect("session-a");

        assertNull(lifecycle.authenticateSession("session-a"));
        verify(expiryFuture).cancel(false);
        verify(firstSocket).close(CloseStatus.NORMAL);
    }

    @Test
    void newValidConnectionCanReconnectAfterAnOldSessionExpires() {
        Instant firstExpiry = now.get().plusSeconds(2);
        when(jwtService.extractExpiration("jwt-old")).thenReturn(firstExpiry);
        when(jwtService.extractExpiration("jwt-new")).thenReturn(firstExpiry.plusSeconds(60));
        when(authenticationService.authenticateToken("jwt-new")).thenReturn(alice);
        assertTrue(lifecycle.registerAuthenticatedSession("session-a", "jwt-old"));
        now.set(firstExpiry);
        assertNull(lifecycle.authenticateSession("session-a"));

        WebSocketSession newSocket = socket("session-new");
        lifecycle.onTransportEstablished(newSocket);
        assertTrue(lifecycle.registerAuthenticatedSession("session-new", "jwt-new"));
        assertSame(alice, lifecycle.authenticateSession("session-new"));
    }

    private WebSocketSession socket(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
