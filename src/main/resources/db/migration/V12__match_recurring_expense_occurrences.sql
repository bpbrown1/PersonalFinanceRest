CREATE TABLE recurring_expense_match (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    recurring_expense_id UUID NOT NULL,
    due_date DATE NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_recurring_expense_match_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_recurring_expense_match_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expense (id),
    CONSTRAINT fk_recurring_expense_match_transaction
        FOREIGN KEY (transaction_id) REFERENCES financial_transaction (id),
    CONSTRAINT uq_recurring_expense_match_occurrence
        UNIQUE (recurring_expense_id, due_date),
    CONSTRAINT uq_recurring_expense_match_transaction
        UNIQUE (transaction_id)
);

CREATE INDEX idx_recurring_expense_match_owner_due
    ON recurring_expense_match (owner_id, due_date);

CREATE INDEX idx_recurring_expense_match_expense_due
    ON recurring_expense_match (recurring_expense_id, due_date);
