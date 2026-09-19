-- Scaffolding for a real payment gateway integration, not wired to any endpoint yet -- see
-- com.nukkad.payment.provider.PaymentProvider and the final report for why (no gateway is
-- selected/integrated in V1; nothing in the current product requires one). provider_reference is
-- the provider's own event/order id and is unique per provider so a duplicate webhook delivery
-- (once a real provider exists) can never be applied twice -- the actual dedup enforcement point
-- once that code path exists, backed here at the DB level.

CREATE TABLE payments (
    id                      CHAR(36) NOT NULL,
    wallet_id               CHAR(36) NOT NULL,
    amount_minor_units      BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    provider                VARCHAR(30) NOT NULL DEFAULT 'NONE',
    provider_reference      VARCHAR(100) NULL,
    idempotency_key         VARCHAR(100) NULL,
    wallet_transaction_id   CHAR(36) NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at            TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_wallet_transaction FOREIGN KEY (wallet_transaction_id) REFERENCES wallet_transactions(id),
    CONSTRAINT uq_payments_provider_reference UNIQUE (provider, provider_reference),
    CONSTRAINT uq_payments_idem UNIQUE (wallet_id, idempotency_key),
    CONSTRAINT chk_payments_amount_positive CHECK (amount_minor_units > 0),
    KEY idx_payments_wallet_created (wallet_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
