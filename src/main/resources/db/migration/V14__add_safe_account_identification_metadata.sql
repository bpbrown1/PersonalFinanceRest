ALTER TABLE financial_account
    ADD COLUMN normalized_name VARCHAR(100);

ALTER TABLE financial_account
    ADD COLUMN institution_name VARCHAR(100);

ALTER TABLE financial_account
    ADD COLUMN account_number_last_four VARCHAR(4);

UPDATE financial_account
SET normalized_name = LOWER(TRIM(name));

ALTER TABLE financial_account
    ALTER COLUMN normalized_name SET NOT NULL;

ALTER TABLE financial_account
    ADD CONSTRAINT chk_financial_account_number_last_four
        CHECK (
            account_number_last_four IS NULL
            OR (
                CHAR_LENGTH(account_number_last_four) = 4
                AND SUBSTRING(account_number_last_four FROM 1 FOR 1) BETWEEN '0' AND '9'
                AND SUBSTRING(account_number_last_four FROM 2 FOR 1) BETWEEN '0' AND '9'
                AND SUBSTRING(account_number_last_four FROM 3 FOR 1) BETWEEN '0' AND '9'
                AND SUBSTRING(account_number_last_four FROM 4 FOR 1) BETWEEN '0' AND '9'
            )
        );

CREATE INDEX idx_financial_account_owner_active_normalized_name
    ON financial_account (owner_id, archived_at, normalized_name);
