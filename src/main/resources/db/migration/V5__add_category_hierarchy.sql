ALTER TABLE transaction_category
    ADD COLUMN parent_id UUID;

ALTER TABLE transaction_category
    ADD CONSTRAINT uq_transaction_category_owner_id
        UNIQUE (owner_id, id);

ALTER TABLE transaction_category
    ADD CONSTRAINT fk_transaction_category_parent
        FOREIGN KEY (owner_id, parent_id) REFERENCES transaction_category (owner_id, id);

CREATE INDEX idx_transaction_category_owner_parent
    ON transaction_category (owner_id, parent_id);
