package com.nukkad.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code adminBaseUrl} is the admin portal's own origin (e.g. https://admin.buildadda.com). Admin
 * emails must link there, never to {@code frontendBaseUrl}: the member site refuses admin accounts.
 *
 * <p>{@code adminPasswordResetEnabled} switches the emailed admin "forgot password" flow on. It stays
 * off until a real mail provider is configured: with none, a reset request would be recorded and
 * acknowledged ("a link has been sent") while no email leaves the server. Changing the password from
 * inside the admin portal never depends on it.
 */
@ConfigurationProperties(prefix = "nukkad.mail")
public record MailProperties(String from, String frontendBaseUrl, String adminBaseUrl, boolean adminPasswordResetEnabled) {
}
