package com.nukkad.program.entity;

/** The two BuildAdda programs that exist today. Deliberately a fixed enum rather than a
 *  database-backed catalog: only SPARK and IGNITE exist, their content (copy, journey, benefits)
 *  comes from the product spec rather than changing at runtime, and a full CMS for exactly two
 *  fixed programs would be over-engineering (see ProgramCatalog's own doc comment). The handful of
 *  values that genuinely vary operationally (fee, enrollment info, whether applications are open)
 *  live in {@link ProgramSettings}, which Admin can edit. */
public enum Program {
    SPARK, IGNITE;

    public static Program fromSlug(String slug) {
        if (slug == null) throw new IllegalArgumentException("Unknown program: null");
        try {
            return Program.valueOf(slug.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown program: " + slug);
        }
    }
}
