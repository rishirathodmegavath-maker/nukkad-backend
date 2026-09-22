package com.nukkad.investor.service;

import com.nukkad.investor.entity.InvestorType;

import java.util.List;
import java.util.Set;

/**
 * One parsed, normalized CSV data row — the parser's output and the import service's input. Nothing here has
 * touched the database yet. {@code hardError} set means the row cannot become an Investor at all (currently:
 * a blank company name) and the import service records it as an ERROR issue and moves on; {@code warnings}
 * are anomalies the row still imports despite (e.g. an unrecognised investor_type defaulting to Other).
 */
public record InvestorCsvRow(
        int rowNumber,
        String externalSourceId,
        String name,
        String rawInvestorType,
        /** Null when {@code rawInvestorType} didn't resolve to a known {@link InvestorType} — the import
         *  service defaults to {@link InvestorType#OTHER} and records a warning; never fabricated here. */
        InvestorType resolvedInvestorType,
        String description,
        String location,
        String country,
        String website,
        String domain,
        Set<String> sectors,
        Set<String> programs,
        Integer investmentCount,
        Integer exitCount,
        Set<String> keyPeople,
        String facebookUrl,
        String instagramUrl,
        String linkedinUrl,
        String twitterUrl,
        String contactEmail,
        Boolean contactEmailVerified,
        String secondaryEmail,
        String phoneNumber,
        List<String> warnings,
        String hardError
) {
    public boolean hasHardError() {
        return hardError != null;
    }
}
