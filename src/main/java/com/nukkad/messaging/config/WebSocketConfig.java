package com.nukkad.messaging.config;

import com.nukkad.security.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(CorsProperties corsProperties, StompAuthChannelInterceptor authChannelInterceptor) {
        this.corsProperties = corsProperties;
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // A single-instance in-memory broker is enough for an MVP demo. Scaling to multiple
        // backend instances/thousands of concurrent sockets means swapping this for
        // registry.enableStompBrokerRelay(...) against an external broker (e.g. RabbitMQ) —
        // a deployment change, not an application-code change.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        String[] origins = java.util.Arrays.stream(corsProperties.allowedOrigins().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOrigins(origins).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
