package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devflow.devflow.security.CurrentUserAuthenticationService;
import com.devflow.devflow.security.JwtStompChannelInterceptor;

@ExtendWith(MockitoExtension.class)
class JwtStompChannelInterceptorTests {

    @Mock private CurrentUserAuthenticationService authenticationService;

    @Test
    void connectUsesTheExistingJwtToSetTheWebSocketPrincipal() {
        when(authenticationService.authenticateToken("valid-token")).thenReturn(new UsernamePasswordAuthenticationToken(
            "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER"))));
        JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(authenticationService);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertSame(message, result);
        assertEquals("user@example.com", accessor.getUser().getName());
        assertEquals(List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")),
            ((UsernamePasswordAuthenticationToken) accessor.getUser()).getAuthorities());
    }

    @Test
    void connectWithoutValidJwtIsRejected() {
        JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(authenticationService);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertNull(interceptor.preSend(message, null));
    }

    @Test
    void privateNotificationsAllowOnlyTheUserQueueSubscription() {
        when(authenticationService.authenticateEmail("user@example.com"))
            .thenReturn(new UsernamePasswordAuthenticationToken("user@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER"))));
        JwtStompChannelInterceptor interceptor = new JwtStompChannelInterceptor(authenticationService);
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
            "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));

        StompHeaderAccessor ownDestination = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        ownDestination.setDestination("/user/queue/notifications");
        ownDestination.setUser(principal);
        Message<byte[]> ownMessage = MessageBuilder.createMessage(new byte[0], ownDestination.getMessageHeaders());
        assertSame(ownMessage, interceptor.preSend(ownMessage, null));

        StompHeaderAccessor anotherUserDestination = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        anotherUserDestination.setDestination("/user/other@example.com/queue/notifications");
        anotherUserDestination.setUser(principal);
        Message<byte[]> anotherUserMessage = MessageBuilder.createMessage(
            new byte[0], anotherUserDestination.getMessageHeaders());
        assertNull(interceptor.preSend(anotherUserMessage, null));
    }
}
