package com.personalfinance.personfinancerest.budget;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "budget")
public class Budget {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private BudgetPeriodType periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL)
    @OrderBy("position ASC, createdAt ASC")
    private List<BudgetLine> lines = new ArrayList<>();

    protected Budget() {
    }

    Budget(UUID id, UUID ownerId, String name, String currency, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.ownerId = ownerId;
        this.periodType = BudgetPeriodType.MONTHLY;
        replace(name, currency, startDate, endDate);
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

    void replace(String name, String currency, LocalDate startDate, LocalDate endDate) {
        this.name = name.trim();
        this.currency = currency.toUpperCase(Locale.ROOT);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    BudgetLine addLine(UUID categoryId, BigDecimal plannedAmount) {
        BudgetLine line = new BudgetLine(UUID.randomUUID(), this, categoryId, plannedAmount, lines.size());
        lines.add(line);
        markChanged();
        return line;
    }

    void reorder(List<UUID> lineIds) {
        for (int position = 0; position < lineIds.size(); position++) {
            UUID lineId = lineIds.get(position);
            lines.stream().filter(line -> line.getId().equals(lineId)).findFirst().orElseThrow()
                    .moveTo(position);
        }
        lines.sort(Comparator.comparingInt(BudgetLine::getPosition));
        markChanged();
    }

    void archive(Instant archivedAt) {
        if (this.archivedAt == null) {
            this.archivedAt = archivedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    void restore() {
        archivedAt = null;
    }

    void markChanged() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
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

    public String getCurrency() {
        return currency;
    }

    public BudgetPeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<BudgetLine> getLines() {
        return List.copyOf(lines);
    }

    public BigDecimal getTotalPlanned() {
        return lines.stream()
                .filter(line -> line.getStatus() == BudgetStatus.ACTIVE)
                .map(BudgetLine::getPlannedAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public BudgetStatus getStatus() {
        return archivedAt == null ? BudgetStatus.ACTIVE : BudgetStatus.ARCHIVED;
    }
}
