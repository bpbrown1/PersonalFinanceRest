package com.personalfinance.personfinancerest.recurringexpense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "recurring_expense")
public class RecurringExpense {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "interval_months", nullable = false)
    private int intervalMonths;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecurringExpense() {
    }

    RecurringExpense(UUID id, UUID ownerId, String name, BigDecimal amount, String currency,
                     UUID categoryId, UUID accountId, LocalDate anchorDate, LocalDate endDate,
                     int intervalMonths) {
        this.id = id;
        this.ownerId = ownerId;
        replace(name, amount, currency, categoryId, accountId, anchorDate, endDate, intervalMonths);
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

    void replace(String name, BigDecimal amount, String currency, UUID categoryId, UUID accountId,
                 LocalDate anchorDate, LocalDate endDate, int intervalMonths) {
        this.name = name.trim();
        this.amount = amount;
        this.currency = currency.toUpperCase(Locale.ROOT);
        this.categoryId = categoryId;
        this.accountId = accountId;
        this.anchorDate = anchorDate;
        this.endDate = endDate;
        this.intervalMonths = intervalMonths;
    }

    void archive(Instant time) {
        if (archivedAt == null) {
            archivedAt = time.truncatedTo(ChronoUnit.MICROS);
        }
    }

    void restore() {
        archivedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getAccountId() { return accountId; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getIntervalMonths() { return intervalMonths; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public RecurringExpenseStatus getStatus() {
        return archivedAt == null ? RecurringExpenseStatus.ACTIVE : RecurringExpenseStatus.ARCHIVED;
    }
}
