package com.nukkad.wallet.dto;

/** The token to send as {@code X-Wallet-Token} on wallet requests until it expires. */
public record WalletUnlockDto(String unlockToken, long expiresInSeconds) {
}
