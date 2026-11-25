CREATE TABLE account_audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    account_id UUID NOT NULL REFERENCES wallet_accounts(id),
    action VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_account_audit_action CHECK (action IN ('ACCOUNT_FROZEN', 'ACCOUNT_UNFROZEN'))
);
CREATE INDEX ix_account_audit_created ON account_audit_logs(created_at DESC, id DESC);
