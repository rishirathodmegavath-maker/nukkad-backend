package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectWithdrawalRequest(@NotBlank @Size(max = 500) String reason) {
}
