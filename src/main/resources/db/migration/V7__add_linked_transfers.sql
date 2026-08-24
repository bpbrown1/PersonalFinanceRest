ALTER TABLE financial_transaction ADD COLUMN transfer_id UUID;

ALTER TABLE financial_transaction DROP CONSTRAINT chk_financial_transaction_type;
ALTER TABLE financial_transaction ADD CONSTRAINT chk_financial_transaction_type
    CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER_OUT', 'TRANSFER_IN'));

ALTER TABLE financial_transaction ADD CONSTRAINT chk_financial_transaction_transfer_shape
    CHECK (
        (type IN ('INCOME', 'EXPENSE') AND transfer_id IS NULL)
        OR
        (type IN ('TRANSFER_OUT', 'TRANSFER_IN') AND transfer_id IS NOT NULL AND category_id IS NULL)
    );

ALTER TABLE financial_transaction ADD CONSTRAINT uq_financial_transaction_transfer_leg
    UNIQUE (transfer_id, type);

CREATE INDEX idx_financial_transaction_owner_transfer
    ON financial_transaction (owner_id, transfer_id);
