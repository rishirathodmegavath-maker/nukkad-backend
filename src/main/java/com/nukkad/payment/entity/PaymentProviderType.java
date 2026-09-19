package com.nukkad.payment.entity;

/**
 * {@code NONE} is the only value in V1 — no real payment gateway is integrated (see
 * com.nukkad.payment.provider.PaymentProvider and the Wallet/Payments implementation report for
 * why). A real integration adds its own literal here (e.g. {@code RAZORPAY}) alongside a
 * {@code PaymentProvider} implementation — this enum is not meant to be extended silently by
 * inventing a fake provider.
 */
public enum PaymentProviderType {
    NONE
}
