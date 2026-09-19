package com.nukkad.wallet.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequestWithdrawalRequest(@Positive long amountMinorUnits, @Size(max = 500) String note) {
}
