package com.nukkad.user.dto;

import java.time.LocalDate;

public record CertificationDto(String id, String title, String issuingOrg, LocalDate issueDate, LocalDate expiryDate,
                                String credentialId, String credentialUrl, int sortOrder) {
}
