package com.nukkad.admin.dto;

import jakarta.validation.constraints.Size;

public record UpdateProgramSettingsRequest(
        Boolean applicationOpen,
        Integer feeAmount,
        @Size(max = 10) String feeCurrency,
        @Size(max = 500) String enrollmentInfo,
        Boolean selective
) {
}
