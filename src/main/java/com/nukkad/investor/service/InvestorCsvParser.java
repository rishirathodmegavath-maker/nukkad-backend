package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.investor.entity.InvestorType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an admin-uploaded investor CSV into {@link InvestorCsvRow}s. Column headers are matched
 * case/punctuation-insensitively against the fixed source schema in the product spec (company_name,
 * investor_type, location, country, description, company_url, domain, industries, program,
 * number_of_investments, number_of_exits, key_people, facebook/instagram/linkedin/twitter, contact_email,
 * contact_email_verified?, 2nd_email_100%_verified, phone_number, id) — see {@link #ALIASES}.
 * <p>
 * Deliberately NOT mapped: {@code employees_people_database}. It's in the source schema but absent from the
 * product's explicit field mapping, and its meaning isn't well-defined enough to invent a target field for —
 * it's left as an "unrecognized column" in the preview rather than guessed at (see the "do not fabricate
 * data" requirement this whole pipeline is built around).
 */
@Component
public class InvestorCsvParser {

    private enum Field {
        ID, NAME, INVESTOR_TYPE, LOCATION, COUNTRY, DESCRIPTION, WEBSITE, DOMAIN, INDUSTRIES, PROGRAM,
        INVESTMENT_COUNT, EXIT_COUNT, KEY_PEOPLE, FACEBOOK, INSTAGRAM, LINKEDIN, TWITTER,
        CONTACT_EMAIL, CONTACT_EMAIL_VERIFIED, SECONDARY_EMAIL, PHONE_NUMBER
    }

    private static final Map<String, Field> ALIASES = buildAliases();

    private static Map<String, Field> buildAliases() {
        Map<String, Field> m = new HashMap<>();
        m.put("id", Field.ID);
        m.put("externalid", Field.ID);
        m.put("externalsourceid", Field.ID);
        m.put("companyname", Field.NAME);
        m.put("name", Field.NAME);
        m.put("investorname", Field.NAME);
        m.put("investortype", Field.INVESTOR_TYPE);
        m.put("type", Field.INVESTOR_TYPE);
        m.put("location", Field.LOCATION);
        m.put("country", Field.COUNTRY);
        m.put("description", Field.DESCRIPTION);
        m.put("companyurl", Field.WEBSITE);
        m.put("website", Field.WEBSITE);
        m.put("url", Field.WEBSITE);
        m.put("domain", Field.DOMAIN);
        m.put("industries", Field.INDUSTRIES);
        m.put("industry", Field.INDUSTRIES);
        m.put("sectors", Field.INDUSTRIES);
        m.put("sector", Field.INDUSTRIES);
        m.put("program", Field.PROGRAM);
        m.put("programs", Field.PROGRAM);
        m.put("numberofinvestments", Field.INVESTMENT_COUNT);
        m.put("investments", Field.INVESTMENT_COUNT);
        m.put("numberofexits", Field.EXIT_COUNT);
        m.put("exits", Field.EXIT_COUNT);
        m.put("keypeople", Field.KEY_PEOPLE);
        m.put("facebook", Field.FACEBOOK);
        m.put("instagram", Field.INSTAGRAM);
        m.put("linkedin", Field.LINKEDIN);
        m.put("twitter", Field.TWITTER);
        m.put("contactemail", Field.CONTACT_EMAIL);
        m.put("email", Field.CONTACT_EMAIL);
        m.put("contactemailverified", Field.CONTACT_EMAIL_VERIFIED);
        // "2nd_email_100%_verified" normalizes to "2ndemail100verified".
        m.put("2ndemail100verified", Field.SECONDARY_EMAIL);
        m.put("secondaryemail", Field.SECONDARY_EMAIL);
        m.put("secondemail", Field.SECONDARY_EMAIL);
        m.put("phonenumber", Field.PHONE_NUMBER);
        m.put("phone", Field.PHONE_NUMBER);
        return m;
    }

    /** Real-world synonyms seen in investor datasets (including the ones in the OpenVC reference screenshot:
     *  "Solo angel", "VC firm", "Family office") — resolved on top of {@link InvestorType#fromLabel}'s exact
     *  match, never invented beyond what the six existing catalog types already mean. */
    private static final Map<String, InvestorType> TYPE_SYNONYMS = buildTypeSynonyms();

    private static Map<String, InvestorType> buildTypeSynonyms() {
        Map<String, InvestorType> m = new HashMap<>();
        m.put("angel", InvestorType.ANGEL);
        m.put("soloangel", InvestorType.ANGEL);
        m.put("angelinvestor", InvestorType.ANGEL);
        m.put("vc", InvestorType.VC);
        m.put("vcfirm", InvestorType.VC);
        m.put("venturecapital", InvestorType.VC);
        m.put("venturecapitalfirm", InvestorType.VC);
        m.put("familyoffice", InvestorType.FAMILY_OFFICE);
        m.put("corporatevc", InvestorType.CORPORATE_VC);
        m.put("cvc", InvestorType.CORPORATE_VC);
        m.put("corporateventurecapital", InvestorType.CORPORATE_VC);
        m.put("accelerator", InvestorType.ACCELERATOR);
        m.put("incubator", InvestorType.ACCELERATOR);
        m.put("acceleratorincubator", InvestorType.ACCELERATOR);
        return m;
    }

    public InvestorCsvParseResult parse(InputStream rawInput) {
        try (Reader reader = stripBomAndOpen(rawInput)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .setAllowMissingColumnNames(true)
                    .build();
            CSVParser parser = format.parse(reader);

            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty()) {
                throw new BadRequestException("This file has no header row — the first line must name the columns");
            }

            Map<Field, String> fieldToHeader = new EnumMap<>(Field.class);
            List<String> unrecognized = new ArrayList<>();
            for (String header : headers) {
                if (header == null || header.isBlank()) continue;
                Field field = ALIASES.get(normalize(header));
                if (field != null) fieldToHeader.put(field, header);
                else unrecognized.add(header);
            }

            boolean hasNameColumn = fieldToHeader.containsKey(Field.NAME);
            if (!hasNameColumn) {
                throw new BadRequestException(
                        "No company/investor name column found (expected a header like \"company_name\") — cannot import without it");
            }

            List<InvestorCsvRow> rows = new ArrayList<>();
            int rowNumber = 0;
            for (CSVRecord record : parser) {
                rowNumber++;
                rows.add(parseRow(rowNumber, record, fieldToHeader));
            }

            return new InvestorCsvParseResult(headers, unrecognized, true, fieldToHeader.containsKey(Field.ID), rows);
        } catch (IOException e) {
            throw new BadRequestException("Could not read this file as CSV: " + e.getMessage());
        }
    }

    private InvestorCsvRow parseRow(int rowNumber, CSVRecord record, Map<Field, String> fieldToHeader) {
        List<String> warnings = new ArrayList<>();

        String name = blankToNull(get(record, fieldToHeader, Field.NAME));
        String hardError = (name == null) ? "No company/investor name in this row" : null;

        String rawType = blankToNull(get(record, fieldToHeader, Field.INVESTOR_TYPE));
        InvestorType resolvedType = resolveInvestorType(rawType);
        if (rawType != null && resolvedType == null) {
            warnings.add("Unrecognized investor type \"" + rawType + "\" — defaulted to Other");
        }

        Integer investmentCount = parseIntOrWarn(get(record, fieldToHeader, Field.INVESTMENT_COUNT), "number_of_investments", warnings);
        Integer exitCount = parseIntOrWarn(get(record, fieldToHeader, Field.EXIT_COUNT), "number_of_exits", warnings);
        Boolean contactEmailVerified = parseBooleanOrWarn(get(record, fieldToHeader, Field.CONTACT_EMAIL_VERIFIED), warnings);

        return new InvestorCsvRow(
                rowNumber,
                blankToNull(get(record, fieldToHeader, Field.ID)),
                name,
                rawType,
                resolvedType,
                blankToNull(get(record, fieldToHeader, Field.DESCRIPTION)),
                blankToNull(get(record, fieldToHeader, Field.LOCATION)),
                blankToNull(get(record, fieldToHeader, Field.COUNTRY)),
                normalizeUrl(get(record, fieldToHeader, Field.WEBSITE)),
                normalizeDomain(get(record, fieldToHeader, Field.DOMAIN)),
                splitList(get(record, fieldToHeader, Field.INDUSTRIES)),
                splitList(get(record, fieldToHeader, Field.PROGRAM)),
                investmentCount,
                exitCount,
                splitList(get(record, fieldToHeader, Field.KEY_PEOPLE)),
                normalizeUrl(get(record, fieldToHeader, Field.FACEBOOK)),
                normalizeUrl(get(record, fieldToHeader, Field.INSTAGRAM)),
                normalizeUrl(get(record, fieldToHeader, Field.LINKEDIN)),
                normalizeUrl(get(record, fieldToHeader, Field.TWITTER)),
                blankToNull(get(record, fieldToHeader, Field.CONTACT_EMAIL)),
                contactEmailVerified,
                blankToNull(get(record, fieldToHeader, Field.SECONDARY_EMAIL)),
                blankToNull(get(record, fieldToHeader, Field.PHONE_NUMBER)),
                warnings,
                hardError
        );
    }

    private static String get(CSVRecord record, Map<Field, String> fieldToHeader, Field field) {
        String header = fieldToHeader.get(field);
        if (header == null) return null;
        try {
            return record.isSet(header) ? record.get(header) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private InvestorType resolveInvestorType(String raw) {
        if (raw == null) return null;
        try {
            return InvestorType.fromLabel(raw);
        } catch (IllegalArgumentException ignored) {
            // fall through to synonym matching
        }
        return TYPE_SYNONYMS.get(normalize(raw));
    }

    private static Integer parseIntOrWarn(String raw, String fieldLabel, List<String> warnings) {
        String value = blankToNull(raw);
        if (value == null) return null;
        String digitsOnly = value.replaceAll("[,\\s]", "");
        try {
            return Integer.parseInt(digitsOnly);
        } catch (NumberFormatException e) {
            warnings.add("\"" + value + "\" in " + fieldLabel + " is not a number — left blank");
            return null;
        }
    }

    private static Boolean parseBooleanOrWarn(String raw, List<String> warnings) {
        String value = blankToNull(raw);
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        if (Set.of("true", "yes", "y", "1", "verified").contains(v)) return Boolean.TRUE;
        if (Set.of("false", "no", "n", "0", "unverified").contains(v)) return Boolean.FALSE;
        warnings.add("\"" + value + "\" in contact_email_verified isn't recognized — left blank");
        return null;
    }

    /** Splits on comma, semicolon or pipe (source datasets vary), trims each item, drops empties, and
     *  de-duplicates while preserving first-seen order. */
    private static Set<String> splitList(String raw) {
        String value = blankToNull(raw);
        if (value == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String part : value.split("[;|,]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private static String normalizeUrl(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        if (value.matches("(?i)^https?://.*")) return value;
        return "https://" + value;
    }

    private static String normalizeDomain(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        String v = value.toLowerCase().replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "");
        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);
        return v.isEmpty() ? null : v;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    /** Strips a UTF-8 byte-order mark if present — common in CSVs exported from Excel — so it doesn't get
     *  glued onto the first header's name (e.g. "﻿id" failing to match "id"). */
    private static Reader stripBomAndOpen(InputStream in) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(in, 3);
        byte[] maybeBom = new byte[3];
        int read = pushback.read(maybeBom, 0, 3);
        if (read == 3 && (maybeBom[0] & 0xFF) == 0xEF && (maybeBom[1] & 0xFF) == 0xBB && (maybeBom[2] & 0xFF) == 0xBF) {
            // BOM consumed, don't push back.
        } else if (read > 0) {
            pushback.unread(maybeBom, 0, read);
        }
        return new InputStreamReader(pushback, StandardCharsets.UTF_8);
    }
}
