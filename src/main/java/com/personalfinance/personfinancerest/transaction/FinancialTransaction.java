package com.personalfinance.personfinancerest.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "financial_transaction")
public class FinancialTransaction {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "merchant_payee", length = 255)
    private String merchantPayee;

    @Column(length = 2000)
    private String notes;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialTransaction() {
    }

    FinancialTransaction(UUID id, UUID ownerId, UUID accountId, UUID categoryId, BigDecimal amount,
                         TransactionType type, LocalDate transactionDate, String description,
                         String merchantPayee, String notes, String externalReference) {
        this.id = id;
        this.ownerId = ownerId;
        replace(accountId, categoryId, amount, type, transactionDate, description,
                merchantPayee, notes, externalReference);
    }

    @PrePersist
    void recordCreationTime() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void recordUpdateTime() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    void replace(UUID accountId, UUID categoryId, BigDecimal amount, TransactionType type,
                 LocalDate transactionDate, String description, String merchantPayee,
                 String notes, String externalReference) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.type = type;
        this.transactionDate = transactionDate;
        this.description = description.trim();
        this.merchantPayee = normalizeOptional(merchantPayee);
        this.notes = normalizeOptional(notes);
        this.externalReference = normalizeOptional(externalReference);
    }

    void softDelete(Instant deletedAt) {
        if (this.deletedAt == null) {
            this.deletedAt = deletedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    void restore() {
        deletedAt = null;
    }

    BigDecimal balanceImpact() {
        return type.balanceImpact(amount);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public String getMerchantPayee() {
        return merchantPayee;
    }

    public String getNotes() {
        return notes;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public TransactionStatus getStatus() {
        return deletedAt == null ? TransactionStatus.ACTIVE : TransactionStatus.DELETED;
    }
}
