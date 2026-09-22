package com.nukkad.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Null means "don't change" for every field. Where an explicit clear is meaningful (description, location,
 *  website, linkedInvestorProfileId, and the other free-text fields below), a blank string clears it — same
 *  convention as UpdateResourceRequest. */
public record UpdateInvestorRequest(
        @Size(max = 200, message = "must not be blank or exceed 200 characters") String name,
        String investorType,
        @Size(max = 5000) String description,
        @Size(max = 200) String location,
        @Size(max = 100) String country,
        @Size(max = 300) String website,
        @Size(max = 255) String domain,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> sectors,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> stages,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> programs,
        Set<@Size(max = 100, message = "each item must be 100 characters or fewer") String> keyPeople,
        @PositiveOrZero Integer investmentCount,
        @PositiveOrZero Integer exitCount,
        @PositiveOrZero Long chequeMin,
        @PositiveOrZero Long chequeMax,
        Boolean active,
        Boolean visible,
        @Size(max = 300) String facebookUrl,
        @Size(max = 300) String instagramUrl,
        @Size(max = 300) String linkedinUrl,
        @Size(max = 300) String twitterUrl,
        @Email @Size(max = 255) String contactEmail,
        Boolean contactEmailVerified,
        @Email @Size(max = 255) String secondaryEmail,
        @Size(max = 50) String phoneNumber,
        /** Null means "don't change"; blank string means "unlink". */
        String linkedInvestorProfileId
) {
}
