CREATE TABLE budget (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_budget_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT chk_budget_period_type
        CHECK (period_type IN ('MONTHLY')),
    CONSTRAINT chk_budget_period
        CHECK (start_date <= end_date)
);

CREATE TABLE budget_line (
    id UUID PRIMARY KEY,
    budget_id UUID NOT NULL,
    category_id UUID NOT NULL,
    planned_amount DECIMAL(19, 2) NOT NULL,
    position INTEGER NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_budget_line_budget
        FOREIGN KEY (budget_id) REFERENCES budget (id),
    CONSTRAINT fk_budget_line_category
        FOREIGN KEY (category_id) REFERENCES transaction_category (id),
    CONSTRAINT uq_budget_line_category
        UNIQUE (budget_id, category_id),
    CONSTRAINT chk_budget_line_planned_amount
        CHECK (planned_amount >= 0),
    CONSTRAINT chk_budget_line_position
        CHECK (position >= 0)
);

CREATE INDEX idx_budget_owner_archived_period
    ON budget (owner_id, archived_at, start_date DESC);

CREATE INDEX idx_budget_line_budget_archived_position
    ON budget_line (budget_id, archived_at, position);

CREATE INDEX idx_budget_line_category
    ON budget_line (category_id);
