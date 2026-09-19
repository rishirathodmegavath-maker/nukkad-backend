package com.nukkad.payment.entity;

/**
 * Controlled state machine for a payment's lifecycle — status is never assigned as an arbitrary
 * string; every transition must go through {@link #canTransitionTo}, which PaymentService checks
 * before applying any change. {@code SUCCESS} and {@code FAILED} and {@code CANCELLED} are
 * terminal except that a {@code SUCCESS}ful payment can later move to {@code REFUNDED} — no other
 * transition out of any terminal state is ever valid (SUCCESS → PENDING, REFUNDED → SUCCESS, and
 * FAILED → SUCCESS are all rejected, per the product's explicit requirement).
 */
public enum PaymentStatus {
    CREATED, PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case CREATED -> target == PENDING || target == FAILED || target == CANCELLED;
            case PENDING -> target == SUCCESS || target == FAILED || target == CANCELLED;
            case SUCCESS -> target == REFUNDED;
            case FAILED, CANCELLED, REFUNDED -> false;
        };
    }
}
