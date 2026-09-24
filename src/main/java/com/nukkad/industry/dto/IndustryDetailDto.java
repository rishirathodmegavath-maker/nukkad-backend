package com.nukkad.industry.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Everything Industry Analysis can honestly show for one industry, computed live from real data.
 * There is no market-size/CAGR field here on purpose — the data model has no such metric, and
 * inventing one would misinform whoever reads this page. {@code recentStartupCount} vs.
 * {@code priorStartupCount} (two 90-day windows) is the one real, derived momentum signal
 * available; the frontend can phrase it as a trend without either side fabricating a number.
 */
public record IndustryDetailDto(
        String name,
        String slug,
        long startupCount,
        long investorCount,
        long grantCount,
        Map<String, Long> stageDistribution,
        long recentStartupCount,
        long priorStartupCount,
        Instant asOf) {
}
