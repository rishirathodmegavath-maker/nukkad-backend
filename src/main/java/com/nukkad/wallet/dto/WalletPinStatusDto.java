package com.nukkad.wallet.dto;

/** {@code lockedForSeconds} is 0 unless too many wrong PINs have locked the wallet. */
public record WalletPinStatusDto(boolean hasPin, long lockedForSeconds) {
}
