package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminChangeProgramApplicationStatusRequest(
        @NotBlank String status,
        @Size(max = 1000) String note
) {
}
