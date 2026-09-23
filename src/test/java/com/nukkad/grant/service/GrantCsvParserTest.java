package com.nukkad.grant.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.startup.entity.StartupStage;
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

class GrantCsvParserTest {

    private final GrantCsvParser parser = new GrantCsvParser();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

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
                        row.createCell(c).setCellValue(value.toString());
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
    void parsesAFullyPopulatedRow() {
        String content = "name,provider,provider_type,description,funding_amount,eligibility_criteria,"
                + "eligible_sectors,eligible_stages,application_deadline,application_url\n"
                + "Startup India Seed Fund,Govt of India,Government,Seed funding for startups,Up to 50L,"
                + "DPIIT-recognized startups,\"Fintech,Healthtech\",\"Idea,MVP\",2026-12-31,https://seedfund.startupindia.gov.in\n";

        GrantCsvParseResult result = parser.parse(csv(content));

        assertThat(result.rows()).hasSize(1);
        GrantCsvRow row = result.rows().get(0);
        assertThat(row.name()).isEqualTo("Startup India Seed Fund");
        assertThat(row.provider()).isEqualTo("Govt of India");
        assertThat(row.resolvedProviderType()).isEqualTo(GrantProviderType.GOVERNMENT);
        assertThat(row.fundingAmount()).isEqualTo("Up to 50L");
        assertThat(row.eligibleSectors()).containsExactlyInAnyOrder("Fintech", "Healthtech");
        assertThat(row.eligibleStages()).containsExactlyInAnyOrder(StartupStage.IDEA, StartupStage.MVP);
        assertThat(row.applicationUrl()).isEqualTo("https://seedfund.startupindia.gov.in");
        assertThat(row.deadline()).isNotNull();
        assertThat(row.hasHardError()).isFalse();
        assertThat(row.warnings()).isEmpty();
    }

    @Test
    void recognizesHeaderAliasesForGrantSchemeName() {
        String content = "Grant / scheme name,Provider,Application URL\nMSME Grant,MSME Ministry,https://msme.gov.in\n";
        GrantCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).name()).isEqualTo("MSME Grant");
        assertThat(result.rows().get(0).provider()).isEqualTo("MSME Ministry");
    }

    @Test
    void missingNameIsAHardErrorAndSkipsTheRow() {
        String content = "name,provider,application_url\n,Some Provider,https://example.com\n";
        GrantCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).hasHardError()).isTrue();
        assertThat(result.rows().get(0).hardError()).contains("name");
    }

    @Test
    void missingProviderIsAHardError() {
        String content = "name,provider,application_url\nSome Grant,,https://example.com\n";
        GrantCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).hasHardError()).isTrue();
        assertThat(result.rows().get(0).hardError()).contains("provider");
    }

    @Test
    void missingApplicationUrlIsAHardError() {
        String content = "name,provider,application_url\nSome Grant,Some Provider,\n";
        GrantCsvParseResult result = parser.parse(csv(content));
        assertThat(result.rows().get(0).hasHardError()).isTrue();
        assertThat(result.rows().get(0).hardError()).contains("application URL");
    }

    @Test
    void unrecognizedProviderTypeDefaultsToOtherWithAWarning() {
        String content = "name,provider,provider_type,application_url\nSome Grant,Some Provider,Bank,https://example.com\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.resolvedProviderType()).isEqualTo(GrantProviderType.OTHER);
        assertThat(row.warnings()).anyMatch(w -> w.contains("Bank"));
    }

    @Test
    void providerTypeSynonymGovtResolvesToGovernment() {
        String content = "name,provider,provider_type,application_url\nSome Grant,Some Provider,Govt,https://example.com\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.resolvedProviderType()).isEqualTo(GrantProviderType.GOVERNMENT);
        assertThat(row.warnings()).isEmpty();
    }

    @Test
    void unrecognizedStageIsDroppedWithAWarningButRowStillImports() {
        String content = "name,provider,eligible_stages,application_url\nSome Grant,Some Provider,\"Idea,Unicorn\",https://example.com\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.eligibleStages()).containsExactly(StartupStage.IDEA);
        assertThat(row.warnings()).anyMatch(w -> w.contains("Unicorn"));
        assertThat(row.hasHardError()).isFalse();
    }

    @Test
    void emptyEligibleStagesAndSectorsMeanOpenToEveryStageAndSector() {
        String content = "name,provider,application_url\nSome Grant,Some Provider,https://example.com\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.eligibleStages()).isEmpty();
        assertThat(row.eligibleSectors()).isEmpty();
    }

    @Test
    void isoDeadlineParsesCorrectly() {
        String content = "name,provider,application_url,deadline\nSome Grant,Some Provider,https://example.com,2026-03-15\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.deadline()).isNotNull();
        assertThat(row.warnings()).isEmpty();
    }

    @Test
    void ddMmYyyyDeadlineParsesCorrectly() {
        String content = "name,provider,application_url,deadline\nSome Grant,Some Provider,https://example.com,15-03-2026\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.deadline()).isNotNull();
        assertThat(row.warnings()).isEmpty();
    }

    @Test
    void unparseableDeadlineIsLeftBlankWithAWarningNotAHardError() {
        String content = "name,provider,application_url,deadline\nSome Grant,Some Provider,https://example.com,not-a-date\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.deadline()).isNull();
        assertThat(row.hasHardError()).isFalse();
        assertThat(row.warnings()).anyMatch(w -> w.contains("not-a-date"));
    }

    @Test
    void applicationUrlWithoutSchemeGetsHttpsPrepended() {
        String content = "name,provider,application_url\nSome Grant,Some Provider,example.com/apply\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.applicationUrl()).isEqualTo("https://example.com/apply");
    }

    @Test
    void noHeaderRowThrows() {
        assertThatThrownBy(() -> parser.parse(csv(""))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void noNameColumnThrows() {
        String content = "provider,application_url\nSome Provider,https://example.com\n";
        assertThatThrownBy(() -> parser.parse(csv(content))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void unrecognizedColumnsAreReportedButDoNotBreakParsing() {
        String content = "name,provider,application_url,some_random_column\nSome Grant,Some Provider,https://example.com,whatever\n";
        GrantCsvParseResult result = parser.parse(csv(content));
        assertThat(result.unrecognizedHeaders()).containsExactly("some_random_column");
        assertThat(result.rows()).hasSize(1);
    }

    @Test
    void controlCharactersAndNonBreakingSpacesAreStrippedFromTextFields() {
        String content = "name,provider,application_url\n Some Grant ,Some Provider,https://example.com\n";
        GrantCsvRow row = parser.parse(csv(content)).rows().get(0);
        assertThat(row.name()).isEqualTo("Some Grant");
    }

    @Test
    void parsesAnExcelWorkbookUsingTheSameValidationAsCsv() {
        List<Object[]> rows = List.of(
                new Object[]{"name", "provider", "application_url"},
                new Object[]{"Excel Grant", "Excel Provider", "https://example.com"}
        );
        GrantCsvParseResult result = parser.parseExcel(xlsx(rows));
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).name()).isEqualTo("Excel Grant");
    }

    @Test
    void extraSheetsInAWorkbookAreNotedButNotImported() {
        List<Object[]> sheet1 = List.of(
                new Object[]{"name", "provider", "application_url"},
                new Object[]{"Sheet One Grant", "Provider", "https://example.com"}
        );
        List<Object[]> sheet2 = List.of(
                new Object[]{"name", "provider", "application_url"},
                new Object[]{"Sheet Two Grant", "Provider", "https://example.com"}
        );
        GrantCsvParseResult result = parser.parseExcel(xlsx(sheet1, sheet2));
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).name()).isEqualTo("Sheet One Grant");
        assertThat(result.note()).contains("2 sheets");
    }
}
