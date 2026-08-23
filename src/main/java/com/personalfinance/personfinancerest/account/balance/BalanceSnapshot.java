package com.personalfinance.personfinancerest.account.balance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balance_snapshot")
public class BalanceSnapshot {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BalanceSnapshotSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BalanceSnapshot() {
    }

    public BalanceSnapshot(UUID id, UUID accountId, BigDecimal balance, Instant effectiveAt,
                           BalanceSnapshotSource source) {
        this.id = id;
        this.accountId = accountId;
        this.balance = balance;
        this.effectiveAt = effectiveAt;
        this.source = source;
    }

    @PrePersist
    void recordCreationTime() {
        createdAt = Instant.now();
    }

    public void updateOpeningValues(BigDecimal balance, Instant effectiveAt) {
        if (source != BalanceSnapshotSource.OPENING) {
            throw new IllegalStateException("Only an opening snapshot can be updated");
        }
        this.balance = balance;
        this.effectiveAt = effectiveAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public BalanceSnapshotSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
