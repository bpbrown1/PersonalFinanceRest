package com.personalfinance.personfinancerest.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "budget_line")
public class BudgetLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "planned_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedAmount;

    @Column(nullable = false)
    private int position;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BudgetLine() {
    }

    BudgetLine(UUID id, Budget budget, UUID categoryId, BigDecimal plannedAmount, int position) {
        this.id = id;
        this.budget = budget;
        replace(categoryId, plannedAmount);
        this.position = position;
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

    void replace(UUID categoryId, BigDecimal plannedAmount) {
        this.categoryId = categoryId;
        this.plannedAmount = plannedAmount;
    }

    void moveTo(int position) {
        this.position = position;
    }

    void archive(Instant archivedAt) {
        if (this.archivedAt == null) {
            this.archivedAt = archivedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    void restore() {
        archivedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public BigDecimal getPlannedAmount() {
        return plannedAmount;
    }

    public int getPosition() {
        return position;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public BudgetStatus getStatus() {
        return archivedAt == null ? BudgetStatus.ACTIVE : BudgetStatus.ARCHIVED;
    }
}
