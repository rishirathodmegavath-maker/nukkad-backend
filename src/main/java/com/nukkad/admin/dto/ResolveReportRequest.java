package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code status} must be "RESOLVED" or "DISMISSED" — a report can only move forward out of OPEN,
 *  never be reset back to it through this endpoint. */
public record ResolveReportRequest(@NotBlank String status, @Size(max = 1000) String resolutionNote) {
}
