package com.personalfinance.personfinancerest.account.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "account_reconciliation")
class AccountReconciliation {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "statement_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal statementBalance;

    @Column(name = "calculated_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal calculatedBalance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal difference;

    @Column(name = "ledger_token", nullable = false, length = 64)
    private String ledgerToken;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 2000)
    private String explanation;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountReconciliation() {
    }

    AccountReconciliation(UUID id, UUID ownerId, UUID accountId, LocalDate statementDate,
                          BigDecimal statementBalance, BigDecimal calculatedBalance,
                          BigDecimal difference, String ledgerToken, String idempotencyKey,
                          String explanation) {
        this.id = id;
        this.ownerId = ownerId;
        this.accountId = accountId;
        this.statementDate = statementDate;
        this.statementBalance = statementBalance;
        this.calculatedBalance = calculatedBalance;
        this.difference = difference;
        this.ledgerToken = ledgerToken;
        this.idempotencyKey = idempotencyKey;
        this.explanation = explanation;
    }

    @PrePersist
    void recordCreationTime() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    void attachTransaction(UUID transactionId) {
        this.transactionId = transactionId;
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    UUID getAccountId() {
        return accountId;
    }

    LocalDate getStatementDate() {
        return statementDate;
    }

    BigDecimal getStatementBalance() {
        return statementBalance;
    }

    BigDecimal getCalculatedBalance() {
        return calculatedBalance;
    }

    BigDecimal getDifference() {
        return difference;
    }

    String getLedgerToken() {
        return ledgerToken;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getExplanation() {
        return explanation;
    }

    UUID getTransactionId() {
        return transactionId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
