package com.personalfinance.personfinancerest.account.management;

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
@Table(name = "financial_account")
public class FinancialAccount {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected FinancialAccount() {
    }

    public FinancialAccount(UUID id, UUID ownerId, String name, AccountType type, String currency,
                            LocalDate openingDate, BigDecimal openingBalance) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.openingDate = openingDate;
        this.openingBalance = openingBalance;
        this.currentBalance = openingBalance;
    }

    @PrePersist
    void recordCreationTime() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void recordUpdateTime() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public AccountStatus getStatus() {
        return archivedAt == null ? AccountStatus.ACTIVE : AccountStatus.ARCHIVED;
    }

    void update(String name, AccountType type, String currency, LocalDate openingDate, BigDecimal openingBalance) {
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.openingDate = openingDate;
        this.openingBalance = openingBalance;
    }

    void archive(Instant archivedAt) {
        if (this.archivedAt == null) {
            this.archivedAt = archivedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    void restore() {
        archivedAt = null;
    }

    public void recordCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }
}
