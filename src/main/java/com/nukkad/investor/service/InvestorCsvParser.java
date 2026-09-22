package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.investor.entity.InvestorType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an admin-uploaded investor file (CSV, or an Excel {@code .xlsx} workbook — see {@link #parseExcel})
 * into {@link InvestorCsvRow}s. Column headers are matched case/punctuation-insensitively against the fixed
 * source schema in the product spec (company_name, investor_type, location, country, description,
 * company_url, domain, industries, program, number_of_investments, number_of_exits, key_people,
 * facebook/instagram/linkedin/twitter, contact_email, contact_email_verified?, 2nd_email_100%_verified,
 * phone_number, id) — see {@link #ALIASES}.
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
     *  "Solo angel", "VC firm", "Family office", and the frequent ones found auditing the actual ~115k-row
     *  production dataset this pipeline is built for: "Individual/Angel", "Angel Group", "Micro VC",
     *  "Venture capital company") — resolved on top of {@link InvestorType#fromLabel}'s exact match, never
     *  invented beyond what the six existing catalog types already mean and what the label itself says. */
    private static final Map<String, InvestorType> TYPE_SYNONYMS = buildTypeSynonyms();

    private static Map<String, InvestorType> buildTypeSynonyms() {
        Map<String, InvestorType> m = new HashMap<>();
        m.put("angel", InvestorType.ANGEL);
        m.put("soloangel", InvestorType.ANGEL);
        m.put("angelinvestor", InvestorType.ANGEL);
        m.put("individualangel", InvestorType.ANGEL);
        m.put("angelgroup", InvestorType.ANGEL);
        m.put("vc", InvestorType.VC);
        m.put("vcfirm", InvestorType.VC);
        m.put("venturecapital", InvestorType.VC);
        m.put("venturecapitalfirm", InvestorType.VC);
        m.put("venturecapitalcompany", InvestorType.VC);
        m.put("microvc", InvestorType.VC);
        m.put("familyoffice", InvestorType.FAMILY_OFFICE);
        m.put("familyinvestmentoffice", InvestorType.FAMILY_OFFICE);
        m.put("corporatevc", InvestorType.CORPORATE_VC);
        m.put("cvc", InvestorType.CORPORATE_VC);
        m.put("corporateventurecapital", InvestorType.CORPORATE_VC);
        m.put("accelerator", InvestorType.ACCELERATOR);
        m.put("incubator", InvestorType.ACCELERATOR);
        m.put("acceleratorincubator", InvestorType.ACCELERATOR);
        return m;
    }

    /** Looks up one field's raw string value in a single row, whatever the underlying file format is —
     *  {@link #parse} wraps a {@link CSVRecord}, {@link #parseExcel} wraps a streamed Excel row. Every bit
     *  of field-mapping/validation logic below is written once, against this, and shared by both formats. */
    private interface RowSource {
        String get(String header);
    }

    /** {@link XSSFSheetXMLHandler} formats a numeric, "General"-format cell the way Excel's own General
     *  format does — including switching to scientific notation once a whole number needs more than ~11
     *  digits to display (e.g. a phone number with a country code, entered as a number rather than text:
     *  442037276601 comes back as "4.42037E+11"). None of this pipeline's numeric-looking fields — phone
     *  numbers, investment/exit counts — are ever meant to render that way, so General-format numbers are
     *  rendered as a plain, non-scientific decimal instead. Any cell with an explicit format (currency, a
     *  date, etc.) still uses the normal formatting — this only overrides the "no specific format" case. */
    private static final class PlainNumberDataFormatter extends DataFormatter {
        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString) {
            if (isGeneralFormat(formatIndex, formatString) && !Double.isNaN(value) && !Double.isInfinite(value)) {
                return plainNumberString(value);
            }
            return super.formatRawCellContents(value, formatIndex, formatString);
        }

        private static boolean isGeneralFormat(int formatIndex, String formatString) {
            return formatIndex == 0 || formatString == null || formatString.isBlank() || "General".equalsIgnoreCase(formatString.trim());
        }

        private static String plainNumberString(double value) {
            BigDecimal decimal = BigDecimal.valueOf(value);
            return value == Math.rint(value) ? decimal.toBigInteger().toString() : decimal.stripTrailingZeros().toPlainString();
        }
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
            Map<Field, String> fieldToHeader = mapHeaders(headers);
            List<String> unrecognized = unrecognizedOf(headers);

            List<InvestorCsvRow> rows = new ArrayList<>();
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

            return new InvestorCsvParseResult(headers, unrecognized, true, fieldToHeader.containsKey(Field.ID), rows, null);
        } catch (IOException e) {
            throw new BadRequestException("Could not read this file as CSV: " + e.getMessage());
        }
    }

    /** Same pipeline for an Excel upload — but read via POI's SAX streaming API
     *  ({@link XSSFReader}/{@link XSSFSheetXMLHandler}), never {@code XSSFWorkbook}. A real investor-list
     *  export is exactly the case this pipeline promises to scale to (100k+ rows), and {@code XSSFWorkbook}
     *  parses the entire sheet into an in-memory DOM first — a ~100k-row sheet's XML alone is ~100MB
     *  uncompressed, which reliably OOMs. Streaming processes one row at a time and holds nothing but the
     *  parsed {@link InvestorCsvRow}s.
     *  <p>
     *  Reads only the workbook's first sheet — a second sheet in the same file is a real possibility with
     *  these datasets (e.g. a separate "investors not yet assigned an id" batch with a different column set
     *  entirely) and silently merging it in would mean guessing at a mapping the admin never confirmed, so
     *  it's left out and named in {@link InvestorCsvParseResult#note} instead of being imported — and,
     *  streaming, its content is never even read off disk. */
    public InvestorCsvParseResult parseExcel(InputStream rawInput) {
        try (OPCPackage pkg = OPCPackage.open(rawInput)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheetIterator.hasNext()) {
                throw new BadRequestException("This workbook has no sheets");
            }

            SheetToRowsHandler handler = new SheetToRowsHandler();
            String firstSheetName;
            try (InputStream firstSheet = sheetIterator.next()) {
                firstSheetName = sheetIterator.getSheetName();
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, handler, new PlainNumberDataFormatter(), false));
                xmlReader.parse(new InputSource(firstSheet));
            }

            int extraSheets = 0;
            while (sheetIterator.hasNext()) {
                try (InputStream extra = sheetIterator.next()) {
                    extraSheets++;
                } // content deliberately never parsed — see the method javadoc.
            }

            if (handler.headers == null) {
                throw new BadRequestException("This file has no header row — the first row must name the columns");
            }
            String note = extraSheets == 0 ? null
                    : "This workbook has " + (1 + extraSheets) + " sheets — only the first (\"" + firstSheetName
                      + "\") was imported; the rest were ignored";

            return new InvestorCsvParseResult(handler.headers, handler.unrecognized, true,
                    handler.fieldToHeader.containsKey(Field.ID), handler.rows, note);
        } catch (IOException | OpenXML4JException | SAXException | javax.xml.parsers.ParserConfigurationException e) {
            throw new BadRequestException("Could not read this file as an Excel workbook: " + e.getMessage());
        } catch (RuntimeException e) {
            // POI throws its own unchecked exceptions (e.g. NotOfficeXmlFileException) for a corrupt/non-xlsx upload.
            throw new BadRequestException("Could not read this file as an Excel workbook: " + e.getMessage());
        }
    }

    /** Feeds {@link #parseExcel}'s SAX pass — one row buffered at a time, never the whole sheet. The first
     *  row seen becomes the header row (mirrors {@code setSkipHeaderRecord(true)} on the CSV side); every
     *  row after that is validated through the exact same {@link #parseRow}/{@link #mapHeaders} the CSV path
     *  uses. A non-static inner class so it can call those instance methods directly. */
    private final class SheetToRowsHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Map<Integer, String> currentRow = new HashMap<>();
        private boolean headerRowSeen = false;
        List<String> headers;
        Map<String, Integer> headerColumns;
        Map<Field, String> fieldToHeader;
        List<String> unrecognized;
        final List<InvestorCsvRow> rows = new ArrayList<>();
        int rowNumber = 0;

        @Override
        public void startRow(int rowNum) {
            currentRow.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerRowSeen) {
                headerRowSeen = true;
                List<String> hs = new ArrayList<>();
                Map<String, Integer> cols = new HashMap<>();
                currentRow.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                    String header = blankToNull(e.getValue());
                    if (header == null) return;
                    hs.add(header);
                    cols.put(header, e.getKey());
                });
                if (hs.isEmpty()) return; // headers stays null — caller reports "no header row"
                headers = hs;
                headerColumns = cols;
                fieldToHeader = mapHeaders(headers);
                unrecognized = unrecognizedOf(headers);
                return;
            }
            if (currentRow.values().stream().allMatch(v -> blankToNull(v) == null)) return; // blank row, skip
            rowNumber++;
            Map<Integer, String> snapshot = Map.copyOf(currentRow);
            RowSource src = header -> {
                Integer col = headerColumns.get(header);
                return col == null ? null : blankToNull(snapshot.get(col));
            };
            rows.add(parseRow(rowNumber, src, fieldToHeader));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null || cellReference.isBlank()) return;
            currentRow.put((int) new CellReference(cellReference).getCol(), formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Not a data row — nothing to do.
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
                    "No company/investor name column found (expected a header like \"company_name\") — cannot import without it");
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

    private InvestorCsvRow parseRow(int rowNumber, RowSource src, Map<Field, String> fieldToHeader) {
        List<String> warnings = new ArrayList<>();

        String name = blankToNull(get(src, fieldToHeader, Field.NAME));
        String hardError = (name == null) ? "No company/investor name in this row" : null;

        String rawType = blankToNull(get(src, fieldToHeader, Field.INVESTOR_TYPE));
        InvestorType resolvedType = rawType == null ? null : resolveOneInvestorType(rawType);
        boolean resolvedViaSplit = false;
        if (rawType != null && resolvedType == null) {
            // Some real sources cram more than one category into this field ("Individual/Angel, Venture
            // Capital") — our catalog only has one type per investor, so try each comma/semicolon/pipe-
            // separated part and use the first that resolves. This only runs when the *whole* raw value
            // didn't already match on its own — "Accelerator, Incubator" resolves directly (it's one of the
            // synonyms below), so it's never treated as "multiple types listed".
            for (String part : rawType.split("[;|,]")) {
                resolvedType = resolveOneInvestorType(part.trim());
                if (resolvedType != null) {
                    resolvedViaSplit = true;
                    break;
                }
            }
        }
        if (rawType != null && resolvedType == null) {
            warnings.add("Unrecognized investor type \"" + rawType + "\" — defaulted to Other");
        } else if (resolvedViaSplit) {
            warnings.add("Multiple investor types listed (\"" + rawType + "\") — used the first recognized one: " + resolvedType.getLabel());
        }

        Integer investmentCount = parseIntOrWarn(get(src, fieldToHeader, Field.INVESTMENT_COUNT), "number_of_investments", warnings);
        Integer exitCount = parseIntOrWarn(get(src, fieldToHeader, Field.EXIT_COUNT), "number_of_exits", warnings);
        Boolean contactEmailVerified = parseBooleanOrWarn(get(src, fieldToHeader, Field.CONTACT_EMAIL_VERIFIED), warnings);

        return new InvestorCsvRow(
                rowNumber,
                blankToNull(get(src, fieldToHeader, Field.ID)),
                name,
                rawType,
                resolvedType,
                blankToNull(get(src, fieldToHeader, Field.DESCRIPTION)),
                blankToNull(get(src, fieldToHeader, Field.LOCATION)),
                blankToNull(get(src, fieldToHeader, Field.COUNTRY)),
                normalizeUrl(get(src, fieldToHeader, Field.WEBSITE)),
                normalizeDomain(get(src, fieldToHeader, Field.DOMAIN)),
                splitList(get(src, fieldToHeader, Field.INDUSTRIES)),
                splitList(get(src, fieldToHeader, Field.PROGRAM)),
                investmentCount,
                exitCount,
                splitList(get(src, fieldToHeader, Field.KEY_PEOPLE)),
                normalizeUrl(get(src, fieldToHeader, Field.FACEBOOK)),
                normalizeUrl(get(src, fieldToHeader, Field.INSTAGRAM)),
                normalizeUrl(get(src, fieldToHeader, Field.LINKEDIN)),
                normalizeUrl(get(src, fieldToHeader, Field.TWITTER)),
                blankToNull(get(src, fieldToHeader, Field.CONTACT_EMAIL)),
                contactEmailVerified,
                blankToNull(get(src, fieldToHeader, Field.SECONDARY_EMAIL)),
                blankToNull(get(src, fieldToHeader, Field.PHONE_NUMBER)),
                warnings,
                hardError
        );
    }

    private static String get(RowSource src, Map<Field, String> fieldToHeader, Field field) {
        String header = fieldToHeader.get(field);
        return header == null ? null : src.get(header);
    }

    private InvestorType resolveOneInvestorType(String raw) {
        if (raw == null || raw.isBlank()) return null;
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
