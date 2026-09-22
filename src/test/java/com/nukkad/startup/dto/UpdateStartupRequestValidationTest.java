package com.nukkad.startup.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** What editing a startup accepts. Every field is optional (null leaves it alone), but a value that is sent has to be
 *  usable: a name can't be emptied, and text can't be longer than the create flow allows. */
class UpdateStartupRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static String of(int length) {
        return "x".repeat(length);
    }

    private UpdateStartupRequest request(String name, String problem, String otherTraction, String keywords) {
        return new UpdateStartupRequest(name, null, null, null, null, null, problem, null, null, null, null, null, null,
                null, null, null, null, otherTraction, keywords, null, null, null, null);
    }

    private Set<String> violatedFields(UpdateStartupRequest request) {
        return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void anEmptyUpdateIsAcceptedBecauseEveryFieldIsOptional() {
        assertThat(validator.validate(request(null, null, null, null))).isEmpty();
    }

    @Test
    void aNameCannotBeEmptiedOrJustSpaces() {
        assertThat(violatedFields(request("", null, null, null))).containsExactly("name");
        assertThat(violatedFields(request("   ", null, null, null))).containsExactly("name");
        assertThat(violatedFields(request("\n\t", null, null, null))).containsExactly("name");
    }

    @Test
    void aNameWithLettersIsAcceptedEvenWithSurroundingSpaces() {
        assertThat(validator.validate(request("  Rocket Labs ", null, null, null))).isEmpty();
    }

    @Test
    void textFieldsStopAtTheSameLimitsAsCreate() {
        assertThat(validator.validate(request(of(200), of(5000), of(5000), of(2000)))).isEmpty();
        assertThat(violatedFields(request(of(201), null, null, null))).containsExactly("name");
        assertThat(violatedFields(request("Ok", of(5001), null, null))).containsExactly("problem");
        assertThat(violatedFields(request("Ok", null, of(5001), null))).containsExactly("otherTraction");
        assertThat(violatedFields(request("Ok", null, null, of(2001)))).containsExactly("keywords");
    }
}
