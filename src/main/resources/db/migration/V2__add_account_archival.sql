ALTER TABLE financial_account
    ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_financial_account_owner_archived_at
    ON financial_account (owner_id, archived_at);
