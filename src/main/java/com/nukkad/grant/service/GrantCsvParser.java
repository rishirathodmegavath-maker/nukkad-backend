package com.nukkad.grant.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.startup.entity.StartupStage;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an admin-uploaded grants file (CSV, or a small Excel {@code .xlsx} workbook) into
 * {@link GrantCsvRow}s. Column headers are matched case/punctuation-insensitively against the
 * fixed target schema (grant/scheme name, provider, provider type, description, funding amount,
 * application deadline, eligibility criteria, eligible stages, eligible sectors, application URL)
 * — see {@link #ALIASES}. Mirrors {@code com.nukkad.investor.service.InvestorCsvParser}'s
 * structure and text-hygiene fixes, simplified: grant sheets are expected to be tens to a few
 * hundred rows (manually curated, not a 100k-row data export), so {@code .xlsx} is read via POI's
 * plain {@link WorkbookFactory} rather than the SAX-streaming API the investor importer needs to
 * stay memory-safe at 100k+ rows.
 */
@Component
public class GrantCsvParser {

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<DateTimeFormatter> DEADLINE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private enum Field {
        NAME, PROVIDER, PROVIDER_TYPE, DESCRIPTION, FUNDING_AMOUNT, ELIGIBILITY_CRITERIA,
        ELIGIBLE_SECTORS, ELIGIBLE_STAGES, DEADLINE, APPLICATION_URL
    }

    private static final Map<String, Field> ALIASES = buildAliases();

    private static Map<String, Field> buildAliases() {
        Map<String, Field> m = new HashMap<>();
        m.put("name", Field.NAME);
        m.put("grantname", Field.NAME);
        m.put("grantschemename", Field.NAME);
        m.put("schemename", Field.NAME);
        m.put("provider", Field.PROVIDER);
        m.put("providertype", Field.PROVIDER_TYPE);
        m.put("type", Field.PROVIDER_TYPE);
        m.put("description", Field.DESCRIPTION);
        m.put("fundingamount", Field.FUNDING_AMOUNT);
        m.put("amount", Field.FUNDING_AMOUNT);
        m.put("eligibilitycriteria", Field.ELIGIBILITY_CRITERIA);
        m.put("eligibility", Field.ELIGIBILITY_CRITERIA);
        m.put("eligiblesectors", Field.ELIGIBLE_SECTORS);
        m.put("sectors", Field.ELIGIBLE_SECTORS);
        m.put("sector", Field.ELIGIBLE_SECTORS);
        m.put("eligiblestages", Field.ELIGIBLE_STAGES);
        m.put("stages", Field.ELIGIBLE_STAGES);
        m.put("stage", Field.ELIGIBLE_STAGES);
        m.put("applicationdeadline", Field.DEADLINE);
        m.put("deadline", Field.DEADLINE);
        m.put("lastdate", Field.DEADLINE);
        m.put("applicationurl", Field.APPLICATION_URL);
        m.put("url", Field.APPLICATION_URL);
        m.put("link", Field.APPLICATION_URL);
        return m;
    }

    /** On top of {@link GrantProviderType#fromLabel}'s exact match — real-world sheets often say
     *  "Govt", "Central Government", "Private", etc. rather than the catalog's exact 5 labels. */
    private static final Map<String, GrantProviderType> PROVIDER_TYPE_SYNONYMS = buildProviderTypeSynonyms();

    private static Map<String, GrantProviderType> buildProviderTypeSynonyms() {
        Map<String, GrantProviderType> m = new HashMap<>();
        m.put("govt", GrantProviderType.GOVERNMENT);
        m.put("govtofindia", GrantProviderType.GOVERNMENT);
        m.put("centralgovernment", GrantProviderType.GOVERNMENT);
        m.put("stategovernment", GrantProviderType.GOVERNMENT);
        m.put("incubator", GrantProviderType.ACCELERATOR);
        m.put("private", GrantProviderType.CORPORATE);
        m.put("company", GrantProviderType.CORPORATE);
        m.put("ngo", GrantProviderType.FOUNDATION);
        m.put("nonprofit", GrantProviderType.FOUNDATION);
        m.put("trust", GrantProviderType.FOUNDATION);
        return m;
    }

    private interface RowSource {
        String get(String header);
    }

    public GrantCsvParseResult parse(InputStream rawInput) {
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
            Map<Field, String> fieldToHeader = mapHeaders(headers);
            List<String> unrecognized = unrecognizedOf(headers);

            List<GrantCsvRow> rows = new ArrayList<>();
            int rowNumber = 0;
            for (CSVRecord record : parser) {
                rowNumber++;
                RowSource src = header -> {
                    try {
                        return record.isSet(header) ? record.get(header) : null;
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                };
                rows.add(parseRow(rowNumber, src, fieldToHeader));
            }
            return new GrantCsvParseResult(headers, unrecognized, rows, null);
        } catch (IOException e) {
            throw new BadRequestException("Could not read this file as CSV: " + e.getMessage());
        }
    }

    /** Reads only the workbook's first sheet — an extra sheet is named in {@link GrantCsvParseResult#note}
     *  rather than guessed at, same rationale as the investor importer. Uses the plain, whole-workbook
     *  {@link WorkbookFactory} API (not POI's SAX streaming reader): grant sheets are expected to be a
     *  manually curated list of tens to a few hundred schemes, nowhere near the row count where the
     *  in-memory DOM approach would risk an OOM. */
    public GrantCsvParseResult parseExcel(InputStream rawInput) {
        try (Workbook workbook = WorkbookFactory.create(rawInput)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException("This workbook has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            List<String> headers = null;
            Map<String, Integer> headerColumns = new HashMap<>();
            Map<Field, String> fieldToHeader = null;
            List<String> unrecognized = null;
            List<GrantCsvRow> rows = new ArrayList<>();
            int rowNumber = 0;

            for (Row row : sheet) {
                if (headers == null) {
                    List<String> hs = new ArrayList<>();
                    for (Cell cell : row) {
                        String header = blankToNull(formatter.formatCellValue(cell));
                        if (header == null) continue;
                        hs.add(header);
                        headerColumns.put(header, cell.getColumnIndex());
                    }
                    if (hs.isEmpty()) continue; // a genuinely blank first row — keep looking for headers
                    headers = hs;
                    fieldToHeader = mapHeaders(headers);
                    unrecognized = unrecognizedOf(headers);
                    continue;
                }

                Map<Field, String> finalFieldToHeader = fieldToHeader;
                RowSource src = header -> {
                    Integer col = headerColumns.get(header);
                    if (col == null) return null;
                    Cell cell = row.getCell(col);
                    return cell == null ? null : blankToNull(formatter.formatCellValue(cell));
                };
                boolean blank = headerColumns.values().stream()
                        .allMatch(col -> row.getCell(col) == null || blankToNull(formatter.formatCellValue(row.getCell(col))) == null);
                if (blank) continue;
                rowNumber++;
                rows.add(parseRow(rowNumber, src, finalFieldToHeader));
            }

            if (headers == null) {
                throw new BadRequestException("This file has no header row — the first row must name the columns");
            }

            int extraSheets = workbook.getNumberOfSheets() - 1;
            String note = extraSheets <= 0 ? null
                    : "This workbook has " + workbook.getNumberOfSheets() + " sheets — only the first (\""
                      + sheet.getSheetName() + "\") was imported; the rest were ignored";

            return new GrantCsvParseResult(headers, unrecognized, rows, note);
        } catch (IOException e) {
            throw new BadRequestException("Could not read this file as an Excel workbook: " + e.getMessage());
        } catch (RuntimeException e) {
            // POI throws its own unchecked exceptions (e.g. a corrupt/non-xlsx upload).
            throw new BadRequestException("Could not read this file as an Excel workbook: " + e.getMessage());
        }
    }

    private Map<Field, String> mapHeaders(List<String> headers) {
        Map<Field, String> fieldToHeader = new EnumMap<>(Field.class);
        for (String header : headers) {
            if (header == null || header.isBlank()) continue;
            Field field = ALIASES.get(normalize(header));
            if (field != null) fieldToHeader.put(field, header);
        }
        if (!fieldToHeader.containsKey(Field.NAME)) {
            throw new BadRequestException(
                    "No grant/scheme name column found (expected a header like \"name\") — cannot import without it");
        }
        return fieldToHeader;
    }

    private List<String> unrecognizedOf(List<String> headers) {
        List<String> unrecognized = new ArrayList<>();
        for (String header : headers) {
            if (header == null || header.isBlank()) continue;
            if (!ALIASES.containsKey(normalize(header))) unrecognized.add(header);
        }
        return unrecognized;
    }

    private GrantCsvRow parseRow(int rowNumber, RowSource src, Map<Field, String> fieldToHeader) {
        List<String> warnings = new ArrayList<>();

        String name = blankToNull(get(src, fieldToHeader, Field.NAME));
        String provider = blankToNull(get(src, fieldToHeader, Field.PROVIDER));
        String applicationUrl = normalizeUrl(get(src, fieldToHeader, Field.APPLICATION_URL));

        String hardError = null;
        if (name == null) hardError = "No grant/scheme name in this row";
        else if (provider == null) hardError = "No provider in this row";
        else if (applicationUrl == null) hardError = "No application URL in this row";

        String rawProviderType = blankToNull(get(src, fieldToHeader, Field.PROVIDER_TYPE));
        GrantProviderType resolvedProviderType = resolveProviderType(rawProviderType);
        if (rawProviderType != null && resolvedProviderType == null) {
            warnings.add("Unrecognized provider type \"" + rawProviderType + "\" — defaulted to Other");
        }

        Set<String> rawStages = splitList(get(src, fieldToHeader, Field.ELIGIBLE_STAGES));
        Set<StartupStage> resolvedStages = new LinkedHashSet<>();
        for (String rawStage : rawStages) {
            StartupStage stage = resolveStage(rawStage);
            if (stage != null) resolvedStages.add(stage);
            else warnings.add("Unrecognized stage \"" + rawStage + "\" — dropped (row still imports)");
        }

        String rawDeadline = blankToNull(get(src, fieldToHeader, Field.DEADLINE));
        Instant deadline = null;
        if (rawDeadline != null) {
            deadline = parseDeadline(rawDeadline);
            if (deadline == null) warnings.add("\"" + rawDeadline + "\" in deadline isn't a recognized date — left blank (rolling)");
        }

        return new GrantCsvRow(
                rowNumber,
                name,
                provider,
                rawProviderType,
                resolvedProviderType == null ? GrantProviderType.OTHER : resolvedProviderType,
                blankToNull(get(src, fieldToHeader, Field.DESCRIPTION)),
                blankToNull(get(src, fieldToHeader, Field.FUNDING_AMOUNT)),
                blankToNull(get(src, fieldToHeader, Field.ELIGIBILITY_CRITERIA)),
                splitList(get(src, fieldToHeader, Field.ELIGIBLE_SECTORS)),
                resolvedStages,
                rawDeadline,
                deadline,
                applicationUrl,
                warnings,
                hardError
        );
    }

    private static String get(RowSource src, Map<Field, String> fieldToHeader, Field field) {
        String header = fieldToHeader.get(field);
        return header == null ? null : src.get(header);
    }

    private GrantProviderType resolveProviderType(String raw) {
        if (raw == null) return null;
        try {
            return GrantProviderType.fromLabel(raw);
        } catch (IllegalArgumentException ignored) {
            // fall through to synonym matching
        }
        return PROVIDER_TYPE_SYNONYMS.get(normalize(raw));
    }

    private StartupStage resolveStage(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return StartupStage.fromLabel(raw);
        } catch (IllegalArgumentException ignored) {
            // fall through to normalized matching (handles "Early-Traction", "earlytraction", etc.)
        }
        for (StartupStage stage : StartupStage.values()) {
            if (normalize(stage.getLabel()).equals(normalize(raw))) return stage;
        }
        return null;
    }

    private Instant parseDeadline(String raw) {
        for (DateTimeFormatter fmt : DEADLINE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(raw.trim(), fmt);
                return date.atTime(LocalTime.MAX).atZone(INDIA_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /** Splits on comma, semicolon or pipe, trims each item, drops empties, and de-duplicates while
     *  preserving first-seen order. */
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

    private static String blankToNull(String value) {
        if (value == null) return null;
        String cleaned = sanitizeText(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Same text-hygiene fixes as the investor importer's parser: drops stray C0/C1 control
     *  characters (mis-encoded smart quotes, Word-paste artifacts) and trims a non-breaking space
     *  (U+00A0), which {@code String.trim()}/{@code .strip()} do not remove. */
    private static String sanitizeText(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && !(c >= 0x7F && c <= 0x9F)) {
                sb.append(c);
            }
        }
        int start = 0, end = sb.length();
        while (start < end && isTrimmableSpace(sb.charAt(start))) start++;
        while (end > start && isTrimmableSpace(sb.charAt(end - 1))) end--;
        return sb.substring(start, end);
    }

    private static boolean isTrimmableSpace(char c) {
        return c == ' ' || c == ' ' || c == '﻿' || Character.isWhitespace(c);
    }

    private static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    /** Strips a UTF-8 byte-order mark if present — common in CSVs exported from Excel — so it
     *  doesn't get glued onto the first header's name. */
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
