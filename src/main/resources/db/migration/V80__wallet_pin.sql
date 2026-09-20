-- Wallet PIN: a second factor in front of the wallet, so an unattended or shared signed-in session
-- cannot read a balance or move money without also knowing a PIN the account owner chose.
--
-- 1) wallet_pins: one row per member who has set a PIN. Only a salted hash is stored (see
--    WalletPinService for the extra server-side pepper). failed_attempts/locked_until implement the
--    brute-force lockout and are kept in the database (not memory) so a restart cannot reset them.
--    pin_version is bumped on every set/change/reset/lockout and is embedded in unlock tokens, so
--    changing or resetting the PIN instantly invalidates any wallet session that is already open.
--
-- 2) audit_logs.action is a native MySQL ENUM (see V59/V78/V79 for why every new AuditAction
--    literal needs its own migration): widen it with the four wallet PIN events.

CREATE TABLE wallet_pins (
    user_id          CHAR(36)     NOT NULL,
    pin_hash         VARCHAR(100) NOT NULL,
    pin_version      INT          NOT NULL DEFAULT 1,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMP    NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_wallet_pins_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED',
        'ADMIN_CONTENT_REMOVED','ADMIN_CONTENT_RESTORED','ADMIN_CONTENT_APPROVED','ADMIN_CONTENT_REJECTED',
        'WALLET_ADMIN_ADJUSTMENT','PAYMENT_INITIATED','PAYMENT_SUCCEEDED','PAYMENT_FAILED',
        'WALLET_WITHDRAWAL_REQUESTED','WALLET_WITHDRAWAL_APPROVED','WALLET_WITHDRAWAL_REJECTED',
        'WALLET_STATUS_CHANGED','INVESTOR_ACTIVATION_APPROVED','INVESTOR_ACTIVATION_REJECTED',
        'ADMIN_PASSWORD_RESET_REQUESTED','ADMIN_PASSWORD_RESET_COMPLETED','ADMIN_PASSWORD_CHANGED',
        'WALLET_PIN_SET','WALLET_PIN_CHANGED','WALLET_PIN_RESET','WALLET_PIN_LOCKED'
    ) NOT NULL;
