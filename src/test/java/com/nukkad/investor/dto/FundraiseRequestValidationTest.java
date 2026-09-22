package com.nukkad.investor.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The amounts a fundraise may be saved with: never negative, and a target that is more than zero. */
class FundraiseRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violated(Object request) {
        return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void anUpdateMayLeaveEveryFieldOut() {
        assertThat(validator.validate(new UpdateFundraiseRequest(null, null, null, null, null))).isEmpty();
    }

    @Test
    void anUpdateAcceptsZeroRaisedAndZeroMinimumTicketButNotNegativeAmounts() {
        assertThat(validator.validate(new UpdateFundraiseRequest(1L, 0L, null, null, 0L))).isEmpty();
        assertThat(violated(new UpdateFundraiseRequest(0L, null, null, null, null))).containsExactly("targetAmount");
        assertThat(violated(new UpdateFundraiseRequest(null, -1L, null, null, null))).containsExactly("amountRaised");
        assertThat(violated(new UpdateFundraiseRequest(null, null, null, null, -5L))).containsExactly("minimumTicket");
    }

    @Test
    void useOfFundsStopsAtFiveThousandCharacters() {
        assertThat(validator.validate(new UpdateFundraiseRequest(null, null, null, "x".repeat(5000), null))).isEmpty();
        assertThat(violated(new UpdateFundraiseRequest(null, null, null, "x".repeat(5001), null))).containsExactly("useOfFunds");
        assertThat(violated(new CreateFundraiseRequest("s1", 10L, "MVP", "x".repeat(5001), null))).containsExactly("useOfFunds");
    }

    @Test
    void aNewFundraiseNeedsAPositiveTargetAndCannotHaveANegativeMinimumTicket() {
        assertThat(validator.validate(new CreateFundraiseRequest("s1", 10L, "MVP", null, 0L))).isEmpty();
        assertThat(violated(new CreateFundraiseRequest("s1", 0L, "MVP", null, null))).containsExactly("targetAmount");
        assertThat(violated(new CreateFundraiseRequest("s1", 10L, "MVP", null, -1L))).containsExactly("minimumTicket");
    }
}
