package com.nukkad.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nukkad.jwt")
public record JwtProperties(String secret, long accessExpirationSeconds, long refreshExpirationSeconds) {
}
