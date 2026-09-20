package com.nukkad.wallet.service;

/** Which PINs are too easy to guess. The 6-digit format itself is enforced on the request DTOs. */
public final class WalletPinPolicy {

    private WalletPinPolicy() {
    }

    /** Rejects the PINs an attacker tries first: 111111, 123456, 654321, 121212, 123123. */
    public static boolean isWeak(String pin) {
        if (pin == null || pin.length() != 6) return true;
        if (pin.chars().distinct().count() == 1) return true;

        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            int step = pin.charAt(i) - pin.charAt(i - 1);
            ascending &= step == 1;
            descending &= step == -1;
        }
        if (ascending || descending) return true;

        for (int block : new int[] {2, 3}) {
            if (pin.equals(pin.substring(0, block).repeat(pin.length() / block))) return true;
        }
        return false;
    }
}
