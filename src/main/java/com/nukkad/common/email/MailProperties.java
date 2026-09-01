package com.nukkad.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nukkad.mail")
public record MailProperties(String from, String frontendBaseUrl) {
}
