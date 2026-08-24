ALTER TABLE financial_account
    ADD CONSTRAINT uq_financial_account_owner_id
        UNIQUE (owner_id, id);

CREATE TABLE financial_transaction (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    account_id UUID NOT NULL,
    category_id UUID,
    amount DECIMAL(19, 2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    merchant_payee VARCHAR(255),
    notes VARCHAR(2000),
    external_reference VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_transaction_account
        FOREIGN KEY (owner_id, account_id) REFERENCES financial_account (owner_id, id),
    CONSTRAINT fk_financial_transaction_category
        FOREIGN KEY (owner_id, category_id) REFERENCES transaction_category (owner_id, id),
    CONSTRAINT chk_financial_transaction_amount
        CHECK (amount > 0),
    CONSTRAINT chk_financial_transaction_type
        CHECK (type IN ('INCOME', 'EXPENSE'))
);

CREATE INDEX idx_financial_transaction_owner_deleted_date
    ON financial_transaction (owner_id, deleted_at, transaction_date DESC);

CREATE INDEX idx_financial_transaction_account_date
    ON financial_transaction (account_id, transaction_date DESC);

CREATE INDEX idx_financial_transaction_category_date
    ON financial_transaction (category_id, transaction_date DESC);
