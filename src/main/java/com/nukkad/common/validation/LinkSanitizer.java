package com.nukkad.common.validation;

import com.nukkad.common.exception.BadRequestException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Server-side backstop for every optional, free-text "paste a link" profile field (social links,
 * project/credential/publication URLs, investor website, ...) — the frontend's own {@code safeHref}
 * (src/lib/links.ts) already refuses to render anything but http(s) as a real link, but that only
 * protects rendering, not storage: without this, the backend would still happily save (and later
 * hand back to every viewer) a {@code javascript:} URI or any other scheme.
 *
 * <p>Mirrors {@code StartupService#normalizeAndValidateUrl} (kept there as-is, this doesn't replace
 * it): a bare {@code github.com/me} is treated the way a person means it and gets {@code https://}
 * put in front, rather than being rejected outright — none of these fields' forms normalize the
 * value before submitting, so requiring an already-absolute URL would reject input that silently
 * "worked" (as an inert, non-functional link) before.
 */
public final class LinkSanitizer {

    private LinkSanitizer() {
    }

    /** Null for blank input; otherwise an absolute http(s) URL with a real host. Throws on anything else
     *  (including a non-http(s) scheme such as {@code javascript:}, which is never auto-corrected). */
    public static String normalizeHttpUrl(String raw, String fieldLabel) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        String candidate = trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!web || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException(fieldLabel + " is not a valid URL");
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException(fieldLabel + " is not a valid URL");
        }
        return candidate;
    }
}
