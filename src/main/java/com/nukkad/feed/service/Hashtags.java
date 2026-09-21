package com.nukkad.feed.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the #hashtags in a post's text. One extractor for everything that needs them (saving a post, editing it, the
 * startup backfill, normalising a tag typed into a URL), so the rules can't drift apart.
 *
 * <p>A hashtag is "#" followed by 2 to 50 letters (with their combining marks, so Hindi and similar scripts stay whole),
 * digits or underscores, with at least one letter, and
 * not glued to a preceding word or "#": "#AI" and "(#ai)" count; "issue #12", "#a", "abc#tag", "page#section" and
 * "##tag" don't. Tags are compared lowercase.
 */
public final class Hashtags {

    public static final int MAX_TAG_LENGTH = 50;
    public static final int MAX_TAGS_PER_POST = 10;

    private static final Pattern HASHTAG =
            Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}_#])#([\\p{L}\\p{M}\\p{N}_]{2," + MAX_TAG_LENGTH + "})(?![\\p{L}\\p{M}\\p{N}_])");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final Pattern VALID_TAG = Pattern.compile("[\\p{L}\\p{M}\\p{N}_]{2," + MAX_TAG_LENGTH + "}");

    private Hashtags() {
    }

    /** The distinct tags in {@code content}, lowercase, in order of first use, at most {@link #MAX_TAGS_PER_POST}. */
    public static Set<String> extract(String content) {
        Set<String> tags = new LinkedHashSet<>();
        if (content == null || content.indexOf('#') < 0) return tags;
        Matcher matcher = HASHTAG.matcher(content);
        while (matcher.find() && tags.size() < MAX_TAGS_PER_POST) {
            String tag = matcher.group(1);
            if (HAS_LETTER.matcher(tag).find()) tags.add(tag.toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    /** A tag typed or linked by a person ("#AI", "ai") as stored, or null when it can't be a tag. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String tag = raw.trim();
        if (tag.startsWith("#")) tag = tag.substring(1);
        if (!VALID_TAG.matcher(tag).matches() || !HAS_LETTER.matcher(tag).find()) return null;
        return tag.toLowerCase(Locale.ROOT);
    }
}
