package com.nukkad.resource.entity;

/**
 * The shelves of the resource library. Stored and exchanged as a stable slug (never the display label),
 * so a label can be reworded without touching data. Adding a shelf is a code change on purpose: the
 * member page draws one tile per category.
 */
public enum ResourceCategory {
    FREE_LEARNING("free-learning"),
    TEMPLATES("templates"),
    PLAYBOOKS("playbooks"),
    PROGRAMS("programs"),
    TOOLS("tools"),
    GOVERNMENT("government");

    private final String slug;

    ResourceCategory(String slug) {
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }

    public static ResourceCategory fromSlug(String slug) {
        for (ResourceCategory c : values()) {
            if (c.slug.equalsIgnoreCase(slug)) return c;
        }
        throw new IllegalArgumentException("Unknown resource category: " + slug);
    }
}
