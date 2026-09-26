package com.nukkad.common.publishing;

import com.nukkad.common.exception.BadRequestException;

import java.util.Locale;

/**
 * The fixed set of public display identities an admin may publish content under — reused across
 * every content type an admin can create (Feed, Ideas, Startups, Opportunities, Grants, Events,
 * Resources, Discussions). Exactly one real admin account is ever behind any of these; no separate
 * User row is ever created per identity, and no entity's real creator/owner column ever changes —
 * this only changes the *displayed* name for content the caller has independently marked as
 * unattributed platform content (see each entity's own {@code postedAsPlatform}-style flag). A
 * fixed, code-curated list on purpose — a client can never submit an arbitrary display name.
 * {@code BUILDADDA} is the plain, generic platform identity; the other four are named publishers.
 */
public enum PublisherIdentity {
    BUILDADDA("BuildAdda"), ARJUN_MEHTA("Arjun Mehta"), KARAN_SHAH("Karan Shah"),
    NEEL_KAPOOR("Neel Kapoor"), VIKRAM_RAO("Vikram Rao");

    private final String label;

    PublisherIdentity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** {@code raw} is whatever an admin-create request sent for the identity field: blank/null
     *  falls back to {@code fallbackForBlank} (each caller's own existing default, e.g. Feed's is
     *  {@code ARJUN_MEHTA} for backward compatibility with content created before this field
     *  existed); anything else must name a real constant (case-insensitive) or is rejected — never
     *  silently coerced to a default, so a typo can't quietly become the wrong identity. */
    public static PublisherIdentity parse(String raw, PublisherIdentity fallbackForBlank) {
        if (raw == null || raw.isBlank()) return fallbackForBlank;
        try {
            return PublisherIdentity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown publisher identity: " + raw);
        }
    }
}
