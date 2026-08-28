package com.nukkad.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nukkad.cors")
public record CorsProperties(String allowedOrigins) {
}
