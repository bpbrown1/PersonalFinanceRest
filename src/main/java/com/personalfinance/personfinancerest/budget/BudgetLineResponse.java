package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetLineResponse(
        UUID id,
        int position,
        UUID categoryId,
        BigDecimal plannedAmount,
        BudgetStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
    static BudgetLineResponse from(BudgetLine line) {
        return new BudgetLineResponse(
                line.getId(), line.getPosition(), line.getCategoryId(), line.getPlannedAmount(),
                line.getStatus(), line.getArchivedAt(), line.getCreatedAt(), line.getUpdatedAt()
        );
    }
}
