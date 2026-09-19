package com.nukkad.payment.provider;

import com.nukkad.payment.entity.PaymentProviderType;

/**
 * The abstraction a real payment gateway (Razorpay, Stripe, ...) integrates through, once V1
 * decides which one it needs. No implementation of this interface exists yet — see the
 * Wallet/Payments implementation report for why: Nukkad V1 has no business flow that currently
 * requires real money movement, so there is nothing to pretend to integrate. When a real provider
 * is chosen, its implementation plugs into {@code PaymentService} without changing the
 * {@code Payment} entity, state machine, or ledger it already writes to.
 *
 * Implementations MUST NOT ever receive, log, or store raw card numbers, CVV, or other payment
 * credentials — only provider-issued opaque references (order/session ids) cross this boundary.
 */
public interface PaymentProvider {

    PaymentProviderType type();

    /** Starts a payment with the provider; returns an opaque provider-side reference (e.g. an
     *  order/session id) to store on {@code Payment.providerReference} and hand to the client to
     *  complete the payment on the provider's own hosted flow. Must never return anything a
     *  client could use to claim success on its own — completion is only ever proven by a
     *  verified webhook (see {@link #verifyWebhookSignature}). */
    String createOrder(long amountMinorUnits, String currency, String idempotencyKey);

    /** Verifies a webhook payload's signature against the provider's configured secret (read from
     *  environment configuration, never hardcoded). Must return {@code false} — never throw — for
     *  a payload that fails verification, so callers can uniformly reject-and-log rather than
     *  treat a malformed signature as a server error. A client-side "payment succeeded" callback
     *  is never sufficient proof on its own; only a signature-verified webhook is. */
    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);
}
