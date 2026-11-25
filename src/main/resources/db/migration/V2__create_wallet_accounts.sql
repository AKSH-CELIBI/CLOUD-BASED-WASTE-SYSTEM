CREATE SEQUENCE wallet_account_number_seq START WITH 100000000000 INCREMENT BY 1 MAXVALUE 999999999999 NO CYCLE;

CREATE TABLE wallet_accounts (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    account_number VARCHAR(12),
    account_kind VARCHAR(16) NOT NULL,
    balance NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wallet_account_kind CHECK (account_kind IN ('CUSTOMER', 'SETTLEMENT')),
    CONSTRAINT ck_wallet_account_shape CHECK (
        (account_kind = 'CUSTOMER' AND user_id IS NOT NULL AND account_number ~ '^[0-9]{12}$') OR
        (account_kind = 'SETTLEMENT' AND user_id IS NULL AND account_number IS NULL)
    ),
    CONSTRAINT ck_wallet_balance_nonnegative CHECK (balance >= 0),
    CONSTRAINT ck_wallet_currency_upper CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_wallet_status CHECK (status IN ('ACTIVE', 'FROZEN'))
);

CREATE UNIQUE INDEX uk_wallet_accounts_user ON wallet_accounts(user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uk_wallet_accounts_number ON wallet_accounts(account_number) WHERE account_number IS NOT NULL;
CREATE UNIQUE INDEX uk_wallet_accounts_settlement ON wallet_accounts(account_kind) WHERE account_kind = 'SETTLEMENT';
CREATE INDEX ix_wallet_accounts_status ON wallet_accounts(status);

-- The settlement wallet is the prefunded source for simulated deposits.
INSERT INTO wallet_accounts(id, account_kind, balance, currency, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'SETTLEMENT', 999999999999999.9999, 'INR', 'ACTIVE');
