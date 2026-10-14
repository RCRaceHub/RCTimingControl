package dev.monkeypatch.rctiming.config.websocket;

import dev.monkeypatch.rctiming.security.StompSessionRegistry;
import dev.monkeypatch.rctiming.security.WebSocketJwtChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP WebSocket configuration.
 * Endpoint: /ws/timing — no SockJS (venue LAN in 2026 does not need fallback).
 * JWT validation is at STOMP CONNECT, not at HTTP upgrade: a browser cannot set headers on the upgrade.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor jwtInterceptor;
    private final StompSessionRegistry sessionRegistry;

    public WebSocketConfig(WebSocketJwtChannelInterceptor jwtInterceptor, StompSessionRegistry sessionRegistry) {
        this.jwtInterceptor = jwtInterceptor;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/timing");
        // No .withSockJS() — CLAUDE.md forbids SockJS
    }

    /** Lets a disabled official's sockets be closed (#61). */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessionRegistry);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtInterceptor);
    }
}
