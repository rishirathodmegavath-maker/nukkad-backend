package com.nukkad.common.validation;

import java.util.Locale;

/**
 * Builds the pattern for a case-insensitive "contains" search over user-typed text.
 *
 * <p>Without escaping, a search for {@code %} matches every row and {@code _} matches any single character,
 * because both are LIKE wildcards. Backslash is MySQL's default LIKE escape character (no ESCAPE clause is
 * needed while {@code NO_BACKSLASH_ESCAPES} is off, which is the server default), so escaping the three
 * metacharacters with it makes each one match itself.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** {@code %text%}, lower-cased and trimmed, with {@code \}, {@code %} and {@code _} in the text made literal. */
    public static String contains(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
