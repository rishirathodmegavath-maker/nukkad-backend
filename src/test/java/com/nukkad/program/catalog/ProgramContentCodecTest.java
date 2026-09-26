package com.nukkad.program.catalog;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure Jackson round-trip tests, no Spring context or database — this is the layer that stands
 *  between the DB's JSON text columns and every place that reads them, so it must be verified on
 *  its own, especially the case that matters most: a record field the JSON simply doesn't mention
 *  (e.g. an application field with no placeholder/help text) must come back null, not throw. */
class ProgramContentCodecTest {

    private final ProgramContentCodec codec = new ProgramContentCodec(new ObjectMapper());

    @Test
    void aNullOrBlankColumnReadsBackAsAnEmptyListRatherThanNull() {
        assertThat(codec.readStrings(null)).isEmpty();
        assertThat(codec.readStrings("")).isEmpty();
        assertThat(codec.readJourney(null)).isEmpty();
        assertThat(codec.readBenefits(null)).isEmpty();
        assertThat(codec.readApplicationSteps(null)).isEmpty();
    }

    @Test
    void anEmptyListWritesBackAsNullRatherThanAnEmptyJsonArray() {
        assertThat(codec.writeStrings(List.of())).isNull();
        assertThat(codec.writeStrings(null)).isNull();
    }

    @Test
    void stringListsRoundTripInOrder() {
        List<String> value = List.of("Students", "Working professionals", "First-time founders");
        assertThat(codec.readStrings(codec.writeStrings(value))).containsExactlyElementsOf(value);
    }

    @Test
    void journeyPhasesRoundTripIncludingTheOptionalFields() {
        List<ProgramJourneyPhase> value = List.of(
                new ProgramJourneyPhase(1, "Think", "Understand entrepreneurship."),
                new ProgramJourneyPhase(2, "Explore", "Find problems worth pursuing.", "A longer take.", "You leave with 3 candidate ideas.", "compass"));

        List<ProgramJourneyPhase> roundTripped = codec.readJourney(codec.writeJourney(value));

        assertThat(roundTripped).hasSize(2);
        assertThat(roundTripped.get(0).detailedDescription()).isNull();
        assertThat(roundTripped.get(0).outcome()).isNull();
        assertThat(roundTripped.get(1).detailedDescription()).isEqualTo("A longer take.");
        assertThat(roundTripped.get(1).icon()).isEqualTo("compass");
    }

    @Test
    void benefitsRoundTripWithANullDescriptionAndIcon() {
        List<ProgramBenefit> value = List.of(new ProgramBenefit(0, null, "Live sessions", null));

        List<ProgramBenefit> roundTripped = codec.readBenefits(codec.writeBenefits(value));

        assertThat(roundTripped).hasSize(1);
        assertThat(roundTripped.get(0).title()).isEqualTo("Live sessions");
        assertThat(roundTripped.get(0).icon()).isNull();
        assertThat(roundTripped.get(0).description()).isNull();
    }

    @Test
    void applicationStepsRoundTripNestedFieldsAndOptions() {
        List<ProgramStep> value = List.of(
                new ProgramStep("basic-information", "Basic Information", List.of(
                        new ProgramField("fullName", "Full Name", ProgramFieldType.TEXT, true, List.of()),
                        new ProgramField("currentStatus", "Current Status", ProgramFieldType.SELECT, true,
                                List.of("Student", "Working Professional", "Other")))),
                new ProgramStep("review", "Review & Submit", List.of()));

        List<ProgramStep> roundTripped = codec.readApplicationSteps(codec.writeApplicationSteps(value));

        assertThat(roundTripped).hasSize(2);
        assertThat(roundTripped.get(0).fields()).hasSize(2);
        assertThat(roundTripped.get(0).fields().get(1).options()).containsExactly("Student", "Working Professional", "Other");
        assertThat(roundTripped.get(0).fields().get(0).placeholder()).isNull();
        assertThat(roundTripped.get(1).fields()).isEmpty();
    }

    /** Exactly the shape the V125 migration's seed SQL writes for SPARK/IGNITE (JSON objects that
     *  never mention placeholder/helpText/detailedDescription/outcome/icon at all) — this is what
     *  proves those objects decode correctly, without needing a real database to check it against. */
    @Test
    void aJsonObjectThatOmitsTheOptionalKeysEntirelyStillDecodes() {
        String json = "[{\"id\":\"basic-information\",\"title\":\"Basic Information\",\"fields\":"
                + "[{\"key\":\"fullName\",\"label\":\"Full Name\",\"type\":\"TEXT\",\"required\":true,\"options\":[]}]}]";

        List<ProgramStep> steps = codec.readApplicationSteps(json);

        assertThat(steps).hasSize(1);
        ProgramField field = steps.get(0).fields().get(0);
        assertThat(field.key()).isEqualTo("fullName");
        assertThat(field.placeholder()).isNull();
        assertThat(field.helpText()).isNull();
    }
}
