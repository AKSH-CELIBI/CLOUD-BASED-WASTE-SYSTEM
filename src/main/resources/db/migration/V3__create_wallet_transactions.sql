CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    transaction_type VARCHAR(16) NOT NULL,
    initiated_by_user_id UUID NOT NULL REFERENCES users(id),
    source_account_id UUID REFERENCES wallet_accounts(id),
    destination_account_id UUID REFERENCES wallet_accounts(id),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(64),
    request_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wallet_transaction_type CHECK (transaction_type IN ('DEPOSIT', 'TRANSFER')),
    CONSTRAINT ck_wallet_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_transaction_status CHECK (status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT ck_wallet_transaction_failure CHECK (
        (status = 'COMPLETED' AND failure_code IS NULL) OR
        (status = 'FAILED' AND failure_code IS NOT NULL)
    ),
    CONSTRAINT ck_wallet_transaction_completed_accounts CHECK (
        status = 'FAILED' OR (source_account_id IS NOT NULL AND destination_account_id IS NOT NULL)
    )
);
CREATE INDEX ix_wallet_transactions_source_created ON wallet_transactions(source_account_id, created_at DESC, id DESC);
CREATE INDEX ix_wallet_transactions_destination_created ON wallet_transactions(destination_account_id, created_at DESC, id DESC);
CREATE INDEX ix_wallet_transactions_user_created ON wallet_transactions(initiated_by_user_id, created_at DESC, id DESC);
