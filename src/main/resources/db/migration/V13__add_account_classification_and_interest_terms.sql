ALTER TABLE financial_account
    ADD COLUMN interest_rate DECIMAL(9, 6);

ALTER TABLE financial_account
    ADD COLUMN interest_rate_type VARCHAR(3);

ALTER TABLE financial_account
    ADD CONSTRAINT chk_financial_account_interest_rate_range
        CHECK (interest_rate IS NULL OR interest_rate BETWEEN 0.000000 AND 999.999999);

ALTER TABLE financial_account
    ADD CONSTRAINT chk_financial_account_interest_terms
        CHECK (
            (interest_rate IS NULL AND interest_rate_type IS NULL)
            OR (type IN ('CHECKING', 'SAVINGS') AND interest_rate IS NOT NULL AND interest_rate_type = 'APY')
            OR (type IN ('CREDIT_CARD', 'LOAN') AND interest_rate IS NOT NULL AND interest_rate_type = 'APR')
        );
