package com.nukkad.wallet.entity;

/**
 * A ledger row is only ever inserted at the moment its balance mutation already happened (see
 * WalletService) — so every row that exists today is, by construction, {@code COMPLETED} at
 * creation. The column exists for forward compatibility (a future multi-step flow might insert a
 * row before its effect is final) without needing another migration to introduce it later.
 */
public enum WalletTransactionStatus {
    COMPLETED
}
