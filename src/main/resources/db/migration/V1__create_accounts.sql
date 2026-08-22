CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL
);

INSERT INTO app_user (id, display_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default User');

CREATE TABLE financial_account (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    opening_date DATE NOT NULL,
    opening_balance DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_account_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT chk_financial_account_type
        CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH', 'CREDIT_CARD', 'LOAN'))
);

CREATE INDEX idx_financial_account_owner_id ON financial_account (owner_id);
