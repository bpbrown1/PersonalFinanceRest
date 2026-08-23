package com.personalfinance.personfinancerest.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_category")
public class TransactionCategory {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "active_name_key", length = 100)
    private String activeNameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryApplicability applicability;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionCategory() {
    }

    TransactionCategory(UUID id, UUID ownerId, String name, CategoryApplicability applicability) {
        this.id = id;
        this.ownerId = ownerId;
        this.applicability = applicability;
        rename(name);
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

    void update(String name, CategoryApplicability applicability) {
        rename(name);
        this.applicability = applicability;
    }

    void archive(Instant archivedAt) {
        if (this.archivedAt == null) {
            this.archivedAt = archivedAt;
            activeNameKey = null;
        }
    }

    void restore() {
        archivedAt = null;
        activeNameKey = normalizedName;
    }

    private void rename(String name) {
        this.name = CategoryNames.displayName(name);
        normalizedName = CategoryNames.normalizedName(name);
        if (archivedAt == null) {
            activeNameKey = normalizedName;
        }
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

    String getNormalizedName() {
        return normalizedName;
    }

    public CategoryApplicability getApplicability() {
        return applicability;
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

    public CategoryStatus getStatus() {
        return archivedAt == null ? CategoryStatus.ACTIVE : CategoryStatus.ARCHIVED;
    }
}
