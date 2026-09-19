-- One wallet per user, created lazily on first access (see WalletService.getOrCreateWallet) --
-- there is no bulk backfill for existing users since an unused wallet holds no information a
-- lazily-created one wouldn't. Balance is an integer count of minor currency units (paise for
-- INR) -- never a floating-point type -- and is only ever mutated by WalletService, transactionally
-- and under a row lock (see V61's wallet_transactions for the append-only ledger backing it).

CREATE TABLE wallets (
    id                   CHAR(36) NOT NULL,
    user_id              CHAR(36) NOT NULL,
    currency             VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance_minor_units  BIGINT NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_wallets_balance_non_negative CHECK (balance_minor_units >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
