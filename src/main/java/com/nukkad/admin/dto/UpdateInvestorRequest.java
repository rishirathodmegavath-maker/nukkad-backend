package com.nukkad.admin.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Null means "don't change" for every field. Where an explicit clear is meaningful (description, location,
 *  website, linkedInvestorProfileId), a blank string clears it — same convention as UpdateResourceRequest. */
public record UpdateInvestorRequest(
        @Size(max = 200, message = "must not be blank or exceed 200 characters") String name,
        String investorType,
        @Size(max = 5000) String description,
        @Size(max = 200) String location,
        @Size(max = 300) String website,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> sectors,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> stages,
        @PositiveOrZero Long chequeMin,
        @PositiveOrZero Long chequeMax,
        Boolean active,
        Boolean visible,
        /** Null means "don't change"; blank string means "unlink". */
        String linkedInvestorProfileId
) {
}
