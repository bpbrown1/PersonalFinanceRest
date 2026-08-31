CREATE TABLE transaction_split (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    category_id UUID NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT fk_transaction_split_transaction
        FOREIGN KEY (transaction_id) REFERENCES financial_transaction (id),
    CONSTRAINT fk_transaction_split_category
        FOREIGN KEY (category_id) REFERENCES transaction_category (id),
    CONSTRAINT chk_transaction_split_amount
        CHECK (amount > 0),
    CONSTRAINT chk_transaction_split_position
        CHECK (position >= 0)
);

CREATE INDEX idx_transaction_split_category_transaction
    ON transaction_split (category_id, transaction_id);
