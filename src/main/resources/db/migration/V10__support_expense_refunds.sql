ALTER TABLE financial_transaction
    DROP CONSTRAINT chk_financial_transaction_amount;

ALTER TABLE financial_transaction
    ADD CONSTRAINT chk_financial_transaction_amount
        CHECK (amount <> 0);

ALTER TABLE transaction_split
    DROP CONSTRAINT chk_transaction_split_amount;

ALTER TABLE transaction_split
    ADD CONSTRAINT chk_transaction_split_amount
        CHECK (amount <> 0);
