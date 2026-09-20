package com.nukkad.common.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Thin wrapper over Spring's JavaMailSender for the two transactional auth emails. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public EmailService(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendVerificationEmail(String to, String name, String rawToken) {
        String link = properties.frontendBaseUrl() + "/verify-email?token=" + rawToken;
        send(to, "Verify your Buildadda account", """
                <p>Hi %s,</p>
                <p>Thanks for signing up for Buildadda. Verify your email address to activate your account:</p>
                <p><a href="%s">Verify my email</a></p>
                <p>This link expires in 24 hours. If you didn't create a Buildadda account, you can ignore this email.</p>
                """.formatted(escape(name), link));
    }

    public void sendPasswordResetEmail(String to, String name, String rawToken) {
        String link = properties.frontendBaseUrl() + "/reset-password?token=" + rawToken;
        send(to, "Reset your Buildadda password", """
                <p>Hi %s,</p>
                <p>We received a request to reset your Buildadda password. This link expires in 1 hour:</p>
                <p><a href="%s">Reset my password</a></p>
                <p>If you didn't request this, you can ignore this email — your password won't change.</p>
                """.formatted(escape(name), link));
    }

    /** The token travels in the URL fragment, not the query string: a fragment is never sent to any
     *  server or in a Referer header, so the token can't leak to the fonts/analytics hosts a page
     *  load may contact, nor land in reverse-proxy access logs. */
    public void sendAdminPasswordResetEmail(String to, String name, String rawToken) {
        String link = properties.adminBaseUrl() + "/reset-password#token=" + rawToken;
        send(to, "Reset your Buildadda admin password", """
                <p>Hi %s,</p>
                <p>A password reset was requested for your Buildadda <strong>admin</strong> account. This link expires in 30 minutes and can be used once:</p>
                <p><a href="%s">Reset my admin password</a></p>
                <p>If you didn't request this, ignore this email — your password won't change. Consider changing it if you think someone else knows it.</p>
                """.formatted(escape(name), link));
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(properties.from());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Sending failures shouldn't surface as a 500 to the caller (e.g. register should still
            // succeed even if the mail server hiccups) — the resend-verification flow covers recovery.
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
