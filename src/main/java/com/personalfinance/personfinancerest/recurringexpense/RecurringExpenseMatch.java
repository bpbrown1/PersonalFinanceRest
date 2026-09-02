package com.personalfinance.personfinancerest.recurringexpense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "recurring_expense_match")
class RecurringExpenseMatch {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "recurring_expense_id", nullable = false)
    private UUID recurringExpenseId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecurringExpenseMatch() {
    }

    RecurringExpenseMatch(UUID id, UUID ownerId, UUID recurringExpenseId,
                          LocalDate dueDate, UUID transactionId) {
        this.id = id;
        this.ownerId = ownerId;
        this.recurringExpenseId = recurringExpenseId;
        this.dueDate = dueDate;
        this.transactionId = transactionId;
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

    void replaceTransaction(UUID transactionId) {
        this.transactionId = transactionId;
    }

    UUID getId() { return id; }
    UUID getOwnerId() { return ownerId; }
    UUID getRecurringExpenseId() { return recurringExpenseId; }
    LocalDate getDueDate() { return dueDate; }
    UUID getTransactionId() { return transactionId; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
