package com.devflow.devflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.devflow.devflow.security.JwtStompChannelInterceptor;
import com.devflow.devflow.security.JwtStompSessionLifecycle;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompChannelInterceptor jwtStompChannelInterceptor;
    private final FrontendOriginPolicy frontendOriginPolicy;
    private final JwtStompSessionLifecycle sessionLifecycle;

    public WebSocketConfig(JwtStompChannelInterceptor jwtStompChannelInterceptor,
                           FrontendOriginPolicy frontendOriginPolicy,
                           JwtStompSessionLifecycle sessionLifecycle) {
        this.jwtStompChannelInterceptor = jwtStompChannelInterceptor;
        this.frontendOriginPolicy = frontendOriginPolicy;
        this.sessionLifecycle = sessionLifecycle;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(frontendOriginPolicy.allowedOrigins().toArray(String[]::new))
            .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(
                    org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getSessionId() == null) return message;
                return sessionLifecycle.allowOutbound(accessor.getSessionId()) ? message : null;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessionLifecycle);
    }

}
