ALTER TABLE financial_account
    ADD COLUMN current_balance DECIMAL(19, 2);

UPDATE financial_account
SET current_balance = opening_balance;

ALTER TABLE financial_account
    ALTER COLUMN current_balance SET NOT NULL;

CREATE TABLE account_balance_snapshot (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    balance DECIMAL(19, 2) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_balance_snapshot_account
        FOREIGN KEY (account_id) REFERENCES financial_account (id),
    CONSTRAINT uq_balance_snapshot_account_effective_at
        UNIQUE (account_id, effective_at),
    CONSTRAINT chk_balance_snapshot_source
        CHECK (source IN ('OPENING', 'MANUAL'))
);

INSERT INTO account_balance_snapshot (id, account_id, balance, effective_at, source, created_at)
SELECT id,
       id,
       opening_balance,
       CAST(opening_date AS TIMESTAMP) AT TIME ZONE 'UTC',
       'OPENING',
       created_at
FROM financial_account;

CREATE INDEX idx_balance_snapshot_account_effective_at
    ON account_balance_snapshot (account_id, effective_at DESC);
