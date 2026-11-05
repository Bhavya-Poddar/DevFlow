package com.devflow.devflow.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final CurrentUserAuthenticationService currentUserAuthenticationService;
    private final JwtStompSessionLifecycle sessionLifecycle;

    public JwtStompChannelInterceptor(CurrentUserAuthenticationService currentUserAuthenticationService,
            JwtStompSessionLifecycle sessionLifecycle) {
        this.currentUserAuthenticationService = currentUserAuthenticationService;
        this.sessionLifecycle = sessionLifecycle;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                sessionLifecycle.rejectConnection(accessor.getSessionId());
                return null;
            }

            String token = authorization.substring(7);
            Authentication authentication = currentUserAuthenticationService.authenticateToken(token);
            if (authentication == null) {
                sessionLifecycle.rejectConnection(accessor.getSessionId());
                return null;
            }
            if (!sessionLifecycle.registerAuthenticatedSession(accessor.getSessionId(), token)) {
                sessionLifecycle.rejectConnection(accessor.getSessionId());
                return null;
            }
            accessor.setUser(authentication);
            return message;
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            sessionLifecycle.disconnect(accessor.getSessionId());
            return message;
        }

        Authentication currentAuthentication = sessionLifecycle.authenticateSession(accessor.getSessionId());
        if (currentAuthentication == null) return null;
        accessor.setUser(currentAuthentication);

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            // Notifications are the only current subscription destination. There are no
            // topic publishers or resource-level topic authorization rules yet.
            if (!"/user/queue/notifications".equals(destination)) {
                return null;
            }
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/app/")) {
                return null;
            }
        }

        return message;
    }
}
