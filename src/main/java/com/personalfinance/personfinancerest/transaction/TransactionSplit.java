package com.personalfinance.personfinancerest.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transaction_split")
public class TransactionSplit {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private FinancialTransaction transaction;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int position;

    protected TransactionSplit() {
    }

    TransactionSplit(UUID id, FinancialTransaction transaction, UUID categoryId,
                     BigDecimal amount, int position) {
        this.id = id;
        this.transaction = transaction;
        replace(categoryId, amount, position);
    }

    void replace(UUID categoryId, BigDecimal amount, int position) {
        this.categoryId = categoryId;
        this.amount = amount;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getPosition() {
        return position;
    }
}
