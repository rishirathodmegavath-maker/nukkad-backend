package com.nukkad.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code adminBaseUrl} is the admin portal's own origin (e.g. https://admin.buildadda.com). Admin
 *  emails must link there, never to {@code frontendBaseUrl}: the member site refuses admin accounts. */
@ConfigurationProperties(prefix = "nukkad.mail")
public record MailProperties(String from, String frontendBaseUrl, String adminBaseUrl) {
}
