package com.orbit.config;

import com.orbit.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;

/**
 * STOMP WebSocket wiring.
 *
 * <p>Security: the inbound channel is authenticated — every CONNECT must carry a
 * valid {@code Authorization: Bearer <jwt>} header, and per-user topics
 * ({@code /topic/reports/{userId}}) are bound to the authenticated principal on
 * SUBSCRIBE. Origins are restricted to the configured frontend, not {@code *}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtService jwt;

    // Comma-separated allowed origin patterns; defaults to the configured frontend + localhost dev.
    @Value("${orbit.websocket.allowed-origins:${orbit.frontend.url:http://localhost:3000},http://localhost:*}")
    private String[] allowedOrigins;

    public WebSocketConfig(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    StompPrincipal principal = authenticate(accessor);
                    if (principal == null) {
                        throw new org.springframework.messaging.MessagingException(
                            "WebSocket CONNECT rejected: missing or invalid JWT");
                    }
                    accessor.setUser(principal);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscribe(accessor);
                }
                return message;
            }
        });
    }

    private StompPrincipal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = header.substring(7).trim();
        try {
            if (!jwt.isValid(token)) return null;
            return new StompPrincipal(jwt.getEmail(token), jwt.getUserId(token));
        } catch (Exception e) {
            return null;
        }
    }

    /** Bind per-user topics to the authenticated principal. */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        Principal user = accessor.getUser();
        if (dest == null) return;
        if (user == null) {
            throw new org.springframework.messaging.MessagingException(
                "WebSocket SUBSCRIBE rejected: unauthenticated session");
        }
        if (dest.startsWith("/topic/reports/") && user instanceof StompPrincipal p) {
            String tail = dest.substring("/topic/reports/".length());
            if (p.userId() == null || !String.valueOf(p.userId()).equals(tail)) {
                log.warn("WebSocket SUBSCRIBE denied: {} tried to subscribe to {}", user.getName(), dest);
                throw new org.springframework.messaging.MessagingException(
                    "SUBSCRIBE denied: not your report topic");
            }
        }
    }

    /** Minimal STOMP principal carrying the authenticated email + user id. */
    public record StompPrincipal(String email, Long userId) implements Principal {
        @Override public String getName() { return email; }
    }
}
