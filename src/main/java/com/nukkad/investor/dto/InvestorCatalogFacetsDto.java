package com.nukkad.investor.dto;

import java.util.List;

/**
 * The real, currently-filterable sector and stage values across the investor catalog, sorted and
 * case-insensitively de-duplicated (e.g. "AI" and "ai" collapse to one entry). Unlike investor type, sector and
 * stage have no fixed enum — they're free text set by whoever entered the row (an admin's CSV/Excel import or
 * manual entry) — so the frontend's filter dropdowns are populated from this instead of a hardcoded list, which
 * would otherwise silently exclude real investors whose value doesn't match the guess exactly.
 */
public record InvestorCatalogFacetsDto(List<String> sectors, List<String> stages) {
}
