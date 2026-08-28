package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpsertCertificationRequest(@NotBlank @Size(max = 200) String title,
                                          @Size(max = 200) String issuingOrg,
                                          LocalDate issueDate,
                                          LocalDate expiryDate,
                                          @Size(max = 150) String credentialId,
                                          @Size(max = 300) String credentialUrl) {
}
