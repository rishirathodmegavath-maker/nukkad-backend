package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.investor.entity.InvestorType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestorCsvParserTest {

    private final InvestorCsvParser parser = new InvestorCsvParser();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
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
}
