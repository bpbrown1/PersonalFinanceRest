CREATE TABLE transaction_category (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    active_name_key VARCHAR(100),
    applicability VARCHAR(20) NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transaction_category_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uq_transaction_category_owner_active_name
        UNIQUE (owner_id, active_name_key),
    CONSTRAINT chk_transaction_category_applicability
        CHECK (applicability IN ('INCOME', 'EXPENSE', 'BOTH')),
    CONSTRAINT chk_transaction_category_active_name_key
        CHECK ((archived_at IS NULL AND active_name_key = normalized_name)
            OR (archived_at IS NOT NULL AND active_name_key IS NULL))
);

CREATE INDEX idx_transaction_category_owner_archived_at_name
    ON transaction_category (owner_id, archived_at, normalized_name);
