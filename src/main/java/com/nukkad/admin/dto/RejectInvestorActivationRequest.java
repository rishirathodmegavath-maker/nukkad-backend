package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectInvestorActivationRequest(@NotBlank @Size(max = 500) String reason) {
}
