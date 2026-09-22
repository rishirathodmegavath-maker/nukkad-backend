package com.nukkad.startup.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The limits the create-startup flow relies on. They mirror the columns (VARCHAR / TEXT) so an over-long value is
 *  refused with a message naming the field, instead of failing in the database with something vague. */
class CreateStartupRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static String of(int length) {
        return "x".repeat(length);
    }

    private CreateStartupRequest request(String name, String location, String website, String problem, String revenue, String otherTraction) {
        return new CreateStartupRequest(name, null, null, null, problem, null, null, null, null,
                location, website, null, null, null, revenue, null, null, null, otherTraction, null, null);
    }

    private Set<String> violatedFields(CreateStartupRequest request) {
        return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void aRequestAtEveryLimitIsAccepted() {
        assertThat(validator.validate(request(of(200), of(200), of(500), of(5000), of(200), of(5000)))).isEmpty();
    }

    @Test
    void eachFieldOneOverItsLimitIsRefusedByName() {
        assertThat(violatedFields(request(of(201), null, null, null, null, null))).containsExactly("name");
        assertThat(violatedFields(request("Ok", of(201), null, null, null, null))).containsExactly("location");
        assertThat(violatedFields(request("Ok", null, of(501), null, null, null))).containsExactly("website");
        assertThat(violatedFields(request("Ok", null, null, of(5001), null, null))).containsExactly("problem");
        assertThat(violatedFields(request("Ok", null, null, null, of(201), null))).containsExactly("revenue");
        assertThat(violatedFields(request("Ok", null, null, null, null, of(5001)))).containsExactly("otherTraction");
    }

    @Test
    void aBlankNameIsStillRefused() {
        assertThat(violatedFields(request("   ", null, null, null, null, null))).containsExactly("name");
        assertThat(violatedFields(request(null, null, null, null, null, null))).containsExactly("name");
    }

    @Test
    void theOriginalNineFieldConstructorLeavesEverythingNewUnset() {
        CreateStartupRequest legacy = new CreateStartupRequest("Kartoniq", null, "Tag", "Fintech", "p", "s", "MVP", Set.of("Funding"), null);

        assertThat(validator.validate(legacy)).isEmpty();
        assertThat(legacy.location()).isNull();
        assertThat(legacy.website()).isNull();
        assertThat(legacy.visibility()).isNull();
        assertThat(legacy.fundraisingVisible()).isNull();
    }
}
