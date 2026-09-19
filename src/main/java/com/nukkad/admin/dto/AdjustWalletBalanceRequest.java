package com.nukkad.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The only way an admin can move money in or out of a wallet. There is deliberately no
 * "set balance" field anywhere in this system — {@code amountMinorUnits} is always a delta
 * applied through the same ledger-writing path as every other credit/debit, and {@code reason}
 * is mandatory so every adjustment is self-documenting in the audit log.
 */
public record AdjustWalletBalanceRequest(
        @NotBlank String direction,
        @Positive long amountMinorUnits,
        String currency,
        @NotBlank @Size(max = 500) String reason,
        String idempotencyKey
) {
}
