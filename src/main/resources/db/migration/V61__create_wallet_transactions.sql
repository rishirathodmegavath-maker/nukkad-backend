-- Append-only ledger: rows are only ever inserted, never updated or deleted (no service method
-- exists to do either). A correction is a new, separate compensating transaction referencing the
-- original via reference_type/reference_id, never an edit of the original row.
--
-- idempotency_key + wallet_id are unique together so a retried request (same key) cannot double-
-- apply -- backed by this DB constraint, not just the application-level check in WalletService,
-- per the "database uniqueness must back up application-level checks" requirement. MySQL treats
-- multiple NULLs in a UNIQUE index as distinct, so non-idempotent entries (NULL key) never collide.

CREATE TABLE wallet_transactions (
    id                   CHAR(36) NOT NULL,
    wallet_id            CHAR(36) NOT NULL,
    type                 VARCHAR(10) NOT NULL,
    amount_minor_units   BIGINT NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    reference_type       VARCHAR(40) NULL,
    reference_id         VARCHAR(64) NULL,
    idempotency_key      VARCHAR(100) NULL,
    description          VARCHAR(255) NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wallet_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE,
    CONSTRAINT uq_wallet_transactions_idem UNIQUE (wallet_id, idempotency_key),
    CONSTRAINT chk_wallet_transactions_amount_positive CHECK (amount_minor_units > 0),
    KEY idx_wallet_transactions_wallet_created (wallet_id, created_at DESC),
    KEY idx_wallet_transactions_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
