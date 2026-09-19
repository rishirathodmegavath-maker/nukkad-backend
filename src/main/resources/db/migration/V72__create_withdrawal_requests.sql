-- Unlike wallet_transactions (append-only), this row changes state: PENDING -> APPROVED/REJECTED/
-- CANCELLED. hold_transaction_id/refund_transaction_id point at the wallet_transactions rows that
-- actually move money (see WithdrawalRequest's class comment) -- no FK on those two columns since
-- wallet_transactions is itself append-only and never needs cascading from this table.

CREATE TABLE withdrawal_requests (
    id                     CHAR(36) NOT NULL,
    user_id                CHAR(36) NOT NULL,
    wallet_id              CHAR(36) NOT NULL,
    amount_minor_units     BIGINT NOT NULL,
    currency               VARCHAR(3) NOT NULL,
    note                   VARCHAR(500) NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    hold_transaction_id    CHAR(36) NULL,
    refund_transaction_id  CHAR(36) NULL,
    decision_note          VARCHAR(500) NULL,
    decided_by_admin_id    CHAR(36) NULL,
    decided_at             TIMESTAMP NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_withdrawal_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_withdrawal_requests_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE,
    CONSTRAINT chk_withdrawal_requests_amount_positive CHECK (amount_minor_units > 0),
    KEY idx_withdrawal_requests_user_created (user_id, created_at DESC),
    KEY idx_withdrawal_requests_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
