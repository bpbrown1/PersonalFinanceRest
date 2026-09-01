CREATE TABLE recurring_expense (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    category_id UUID NOT NULL,
    account_id UUID,
    anchor_date DATE NOT NULL,
    end_date DATE,
    interval_months INTEGER NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_recurring_expense_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_recurring_expense_category
        FOREIGN KEY (category_id) REFERENCES transaction_category (id),
    CONSTRAINT fk_recurring_expense_account
        FOREIGN KEY (account_id) REFERENCES financial_account (id),
    CONSTRAINT chk_recurring_expense_amount
        CHECK (amount >= 0),
    CONSTRAINT chk_recurring_expense_interval
        CHECK (interval_months > 0),
    CONSTRAINT chk_recurring_expense_dates
        CHECK (end_date IS NULL OR end_date >= anchor_date)
);

CREATE INDEX idx_recurring_expense_owner_archived_name
    ON recurring_expense (owner_id, archived_at, name);

CREATE INDEX idx_recurring_expense_owner_dates
    ON recurring_expense (owner_id, anchor_date, end_date);

CREATE INDEX idx_recurring_expense_category
    ON recurring_expense (category_id);

CREATE INDEX idx_recurring_expense_account
    ON recurring_expense (account_id);
