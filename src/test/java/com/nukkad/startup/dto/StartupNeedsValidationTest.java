package com.nukkad.startup.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** startup_needs.need is VARCHAR(100). A longer tag used to reach the database and fail there with a vague
 *  "conflicts with existing data"; it is now refused up front with a message that says what to fix. */
class StartupNeedsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static String of(int length) {
        return "x".repeat(length);
    }

    private CreateStartupRequest create(Set<String> needs) {
        return new CreateStartupRequest("Kartoniq", null, null, null, null, null, "Early Traction", needs, null);
    }

    private UpdateStartupRequest update(Set<String> needs) {
        return new UpdateStartupRequest(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, needs);
    }

    @Test
    void aTagOfExactlyTheColumnLimitIsAccepted() {
        assertThat(validator.validate(create(Set.of(of(100))))).isEmpty();
        assertThat(validator.validate(update(Set.of(of(100))))).isEmpty();
    }

    @Test
    void aTagOneOverTheLimitIsRejectedOnCreateAndOnUpdate() {
        Set<ConstraintViolation<CreateStartupRequest>> onCreate = validator.validate(create(Set.of(of(101))));
        Set<ConstraintViolation<UpdateStartupRequest>> onUpdate = validator.validate(update(Set.of(of(101))));

        assertThat(onCreate).hasSize(1);
        assertThat(onCreate.iterator().next().getMessage()).isEqualTo("each item must be 100 characters or fewer");
        assertThat(onUpdate).hasSize(1);
    }

    @Test
    void theTagFromTheBugReportIsRejected() {
        String tag = "Looking for early-stage funding to expand inventory, marketing, delivery operations, "
                + "and customer acquisition across Noida and Greater Noida.";
        assertThat(tag.length()).isGreaterThan(100);

        assertThat(validator.validate(create(Set.of(tag)))).hasSize(1);
    }

    @Test
    void shortTagsAndAMissingListStillPass() {
        assertThat(validator.validate(create(Set.of("Funding", "Engineers", "Mentors")))).isEmpty();
        assertThat(validator.validate(create(null))).isEmpty();
        assertThat(validator.validate(update(null))).isEmpty();
    }

    @Test
    void oneLongTagAmongShortOnesIsStillCaught() {
        assertThat(validator.validate(create(Set.of("Funding", of(150), "Mentors")))).hasSize(1);
    }
}
