ALTER TABLE financial_transaction
    ADD COLUMN provenance VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE financial_transaction
    ADD CONSTRAINT chk_financial_transaction_provenance
        CHECK (provenance IN ('MANUAL', 'IMPORTED', 'RECONCILIATION'));

CREATE TABLE account_reconciliation (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    account_id UUID NOT NULL,
    statement_date DATE NOT NULL,
    statement_balance DECIMAL(19, 2) NOT NULL,
    calculated_balance DECIMAL(19, 2) NOT NULL,
    difference DECIMAL(19, 2) NOT NULL,
    ledger_token VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_account_reconciliation_account
        FOREIGN KEY (owner_id, account_id) REFERENCES financial_account (owner_id, id)
            ON DELETE CASCADE,
    CONSTRAINT fk_account_reconciliation_transaction
        FOREIGN KEY (transaction_id) REFERENCES financial_transaction (id)
            ON DELETE SET NULL,
    CONSTRAINT uq_account_reconciliation_idempotency
        UNIQUE (owner_id, account_id, idempotency_key),
    CONSTRAINT uq_account_reconciliation_transaction
        UNIQUE (transaction_id)
);

ALTER TABLE financial_transaction
    ADD COLUMN reconciliation_id UUID;

ALTER TABLE financial_transaction
    ADD CONSTRAINT fk_financial_transaction_reconciliation
        FOREIGN KEY (reconciliation_id) REFERENCES account_reconciliation (id)
            ON DELETE SET NULL;

ALTER TABLE financial_transaction
    ADD CONSTRAINT uq_financial_transaction_reconciliation
        UNIQUE (reconciliation_id);

CREATE INDEX idx_account_reconciliation_owner_account_created
    ON account_reconciliation (owner_id, account_id, created_at DESC);

CREATE INDEX idx_financial_transaction_account_statement_date
    ON financial_transaction (owner_id, account_id, deleted_at, transaction_date);
