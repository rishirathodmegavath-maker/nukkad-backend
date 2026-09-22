package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.investor.entity.InvestorType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestorCsvParserTest {

    private final InvestorCsvParser parser = new InvestorCsvParser();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a minimal .xlsx in memory — one sheet per {@code sheets} entry, first row headers, rest data.
     *  A cell value that's a {@link Double} is written as a real numeric cell (not text), the way Excel
     *  itself stores a whole-number column like number_of_investments. */
    @SafeVarargs
    private static InputStream xlsx(List<Object[]>... sheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            int sheetIndex = 0;
            for (List<Object[]> sheetRows : sheets) {
                Sheet sheet = workbook.createSheet("Sheet" + (++sheetIndex));
                int rowIndex = 0;
                for (Object[] cells : sheetRows) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int c = 0; c < cells.length; c++) {
                        Object value = cells[c];
                        if (value == null) continue;
                        if (value instanceof Double d) row.createCell(c).setCellValue(d);
                        else row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void parsesAFullyPopulatedRowUsingTheExactSourceColumnNames() {
        String content = "id,company_name,investor_type,location,country,description,company_url,domain,industries,"
                + "program,number_of_investments,number_of_exits,key_people,facebook,instagram,linkedin,twitter,"
                + "contact_email,contact_email_verified?,2nd_email_100%_verified,phone_number,employees_people_database\n"
                + "src-1,Peak Capital,VC,Bangalore,India,Backs bold founders,peak.vc,peak.vc,\"AI; SaaS, FinTech\","
                + "Peak Fellows,\"1,240\",87,\"Asha Rao, Vikram Shah\",fb.com/peak,ig.com/peak,li.com/peak,tw.com/peak,"
                + "hello@peak.vc,yes,verified@peak.vc,+91-9000000000,50-100\n";

        InvestorCsvParseResult result = parser.parse(csv(content));

        assertThat(result.hasNameColumn()).isTrue();
        assertThat(result.hasIdColumn()).isTrue();
        assertThat(result.unrecognizedHeaders()).containsExactly("employees_people_database");
        assertThat(result.rows()).hasSize(1);

        InvestorCsvRow row = result.rows().get(0);
        assertThat(row.hasHardError()).isFalse();
        assertThat(row.externalSourceId()).isEqualTo("src-1");
        assertThat(row.name()).isEqualTo("Peak Capital");
        assertThat(row.resolvedInvestorType()).isEqualTo(InvestorType.VC);
        assertThat(row.location()).isEqualTo("Bangalore");
        assertThat(row.country()).isEqualTo("India");
        assertThat(row.website()).isEqualTo("https://peak.vc");
        assertThat(row.domain()).isEqualTo("peak.vc");
        assertThat(row.sectors()).containsExactlyInAnyOrder("AI", "SaaS", "FinTech");
        assertThat(row.programs()).containsExactly("Peak Fellows");
        assertThat(row.investmentCount()).isEqualTo(1240);
        assertThat(row.exitCount()).isEqualTo(87);
        assertThat(row.keyPeople()).containsExactlyInAnyOrder("Asha Rao", "Vikram Shah");
        assertThat(row.contactEmail()).isEqualTo("hello@peak.vc");
        assertThat(row.contactEmailVerified()).isTrue();
        assertThat(row.secondaryEmail()).isEqualTo("verified@peak.vc");
        assertThat(row.phoneNumber()).isEqualTo("+91-9000000000");
        assertThat(row.warnings()).isEmpty();
    }

    @Test
    void headerMatchingIgnoresCaseSpacingAndPunctuation() {
        String content = "Company Name,Investor Type\nAcme Ventures,Family Office\n";
        InvestorCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).name()).isEqualTo("Acme Ventures");
        assertThat(result.rows().get(0).resolvedInvestorType()).isEqualTo(InvestorType.FAMILY_OFFICE);
    }

    @Test
    void realWorldTypeSynonymsFromTheReferenceDatasetResolveCorrectly() {
        String content = "company_name,investor_type\nA,Solo angel\nB,VC firm\nC,Family office\n";
        InvestorCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).resolvedInvestorType()).isEqualTo(InvestorType.ANGEL);
        assertThat(result.rows().get(1).resolvedInvestorType()).isEqualTo(InvestorType.VC);
        assertThat(result.rows().get(2).resolvedInvestorType()).isEqualTo(InvestorType.FAMILY_OFFICE);
    }

    @Test
    void aMissingNameColumnIsRejectedUpfront() {
        String content = "id,investor_type\n1,VC\n";
        assertThatThrownBy(() -> parser.parse(csv(content))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aBlankNameIsAHardErrorForThatRowOnly() {
        String content = "company_name,investor_type\n,VC\nGood Capital,VC\n";
        InvestorCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).hasHardError()).isTrue();
        assertThat(result.rows().get(1).hasHardError()).isFalse();
    }

    @Test
    void anUnrecognizedInvestorTypeWarnsAndLeavesResolvedTypeNull() {
        String content = "company_name,investor_type\nMystery Capital,Space Pirates\n";
        InvestorCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.resolvedInvestorType()).isNull();
        assertThat(row.warnings()).anyMatch(w -> w.contains("Space Pirates"));
    }

    @Test
    void additionalRealWorldTypeSynonymsFoundInTheProductionDatasetResolveCleanlyWithNoWarning() {
        String content = "company_name,investor_type\nA,Individual/Angel\nB,Angel Group\nC,Micro VC\nD,Venture capital company\n";
        InvestorCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).resolvedInvestorType()).isEqualTo(InvestorType.ANGEL);
        assertThat(result.rows().get(1).resolvedInvestorType()).isEqualTo(InvestorType.ANGEL);
        assertThat(result.rows().get(2).resolvedInvestorType()).isEqualTo(InvestorType.VC);
        assertThat(result.rows().get(3).resolvedInvestorType()).isEqualTo(InvestorType.VC);
        assertThat(result.rows()).allMatch(r -> r.warnings().isEmpty());
    }

    @Test
    void aTypeColumnListingMultipleCategoriesResolvesToTheFirstRecognizedOneWithAWarning() {
        String content = "company_name,investor_type\nA,\"Individual/Angel, Venture Capital\"\nB,\"Private Equity Firm, Individual/Angel\"\n";
        InvestorCsvParseResult result = parser.parse(csv(content));

        InvestorCsvRow first = result.rows().get(0);
        assertThat(first.resolvedInvestorType()).isEqualTo(InvestorType.ANGEL);
        assertThat(first.warnings()).anyMatch(w -> w.contains("Multiple investor types listed") && w.contains("Angel"));

        // "Private Equity Firm" isn't one of the six catalog types and isn't guessed at — but the second,
        // recognized part of the same field ("Individual/Angel") still resolves.
        InvestorCsvRow second = result.rows().get(1);
        assertThat(second.resolvedInvestorType()).isEqualTo(InvestorType.ANGEL);
    }

    @Test
    void aCombinedLabelThatIsItselfAKnownSynonymIsNotTreatedAsMultipleTypes() {
        // "Accelerator, Incubator" is its own direct synonym (both words mean the same catalog type here) —
        // it must resolve silently, not trigger the "multiple types listed" warning meant for genuinely
        // different categories crammed into one field.
        String content = "company_name,investor_type\nA,\"Accelerator, Incubator\"\n";
        InvestorCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.resolvedInvestorType()).isEqualTo(InvestorType.ACCELERATOR);
        assertThat(row.warnings()).isEmpty();
    }

    @Test
    void aTypeColumnWithNoRecognizableCategoryAtAllStillDefaultsToOther() {
        String content = "company_name,investor_type\nA,\"Private Equity Firm, Investment Bank\"\n";
        InvestorCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.resolvedInvestorType()).isNull();
        assertThat(row.warnings()).anyMatch(w -> w.contains("Unrecognized investor type"));
    }

    @Test
    void aNonNumericInvestmentCountWarnsAndIsLeftBlankRatherThanFailingTheRow() {
        String content = "company_name,number_of_investments\nAcme,N/A\n";
        InvestorCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.hasHardError()).isFalse();
        assertThat(row.investmentCount()).isNull();
        assertThat(row.warnings()).anyMatch(w -> w.contains("N/A"));
    }

    @Test
    void noIdColumnIsReportedButDoesNotBlockParsing() {
        String content = "company_name\nAcme\n";
        InvestorCsvParseResult result = parser.parse(csv(content));
        assertThat(result.hasIdColumn()).isFalse();
        assertThat(result.rows().get(0).externalSourceId()).isNull();
    }

    @Test
    void aUtf8ByteOrderMarkOnTheFirstHeaderDoesNotBreakColumnDetection() {
        String content = "﻿company_name,investor_type\nAcme,VC\n";
        InvestorCsvParseResult result = parser.parse(csv(content));
        assertThat(result.hasNameColumn()).isTrue();
        assertThat(result.rows().get(0).name()).isEqualTo("Acme");
    }

    @Test
    void industriesSplitOnCommaSemicolonOrPipeAndDeduplicate() {
        String content = "company_name,industries\nAcme,\"AI|AI;SaaS, FinTech\"\n";
        List<String> sectors = parser.parse(csv(content)).rows().get(0).sectors().stream().sorted().toList();
        assertThat(sectors).containsExactly("AI", "FinTech", "SaaS");
    }

    @Test
    void parseExcelReadsAnXlsxUploadTheSameWayAsCsv() {
        List<Object[]> sheet = List.of(
                new Object[]{"company_name", "investor_type", "number_of_investments"},
                new Object[]{"Peak Capital", "VC", 1240.0});
        InvestorCsvParseResult result = parser.parseExcel(xlsx(sheet));

        assertThat(result.rows()).hasSize(1);
        InvestorCsvRow row = result.rows().get(0);
        assertThat(row.name()).isEqualTo("Peak Capital");
        assertThat(row.resolvedInvestorType()).isEqualTo(InvestorType.VC);
        // A numeric Excel cell must come back as a plain integer ("1240"), not POI's default "1240.0" —
        // that's what number-of-investments parsing checks for.
        assertThat(row.investmentCount()).isEqualTo(1240);
    }

    @Test
    void parseExcelSkipsBlankRowsBetweenDataRows() {
        List<Object[]> sheet = List.of(
                new Object[]{"company_name"},
                new Object[]{"Acme"},
                new Object[]{},
                new Object[]{"Beta"});
        InvestorCsvParseResult result = parser.parseExcel(xlsx(sheet));
        assertThat(result.rows()).extracting(InvestorCsvRow::name).containsExactly("Acme", "Beta");
    }

    @Test
    void parseExcelOnlyImportsTheFirstSheetAndNotesTheRest() {
        List<Object[]> sheet1 = List.of(new Object[]{"company_name"}, new Object[]{"Acme"});
        List<Object[]> sheet2 = List.of(new Object[]{"investor_name"}, new Object[]{"Should not appear"});
        InvestorCsvParseResult result = parser.parseExcel(xlsx(sheet1, sheet2));

        assertThat(result.rows()).extracting(InvestorCsvRow::name).containsExactly("Acme");
        assertThat(result.note()).contains("2 sheets").contains("Sheet1");
    }

    @Test
    void parseExcelWithOneSheetHasNoNote() {
        List<Object[]> sheet = List.of(new Object[]{"company_name"}, new Object[]{"Acme"});
        assertThat(parser.parseExcel(xlsx(sheet)).note()).isNull();
    }

    @Test
    void parseExcelRejectsAFileWithNoHeaderRow() {
        assertThatThrownBy(() -> parser.parseExcel(xlsx(List.<Object[]>of())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void parseExcelRejectsSomethingThatIsNotActuallyAnXlsxFile() {
        assertThatThrownBy(() -> parser.parseExcel(csv("this is plainly not a zip file")))
                .isInstanceOf(BadRequestException.class);
    }
}
