package com.nukkad.industry.dto;

/** An industry derived from real sector data (startups, catalog investors) — one entry per
 *  distinct sector spelling, folded case-insensitively. No fixed taxonomy: this is discovery over
 *  live data, not an admin-curated list. */
public record IndustryDto(String name, String slug, long startupCount) {
}
