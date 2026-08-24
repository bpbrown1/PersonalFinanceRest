package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID ownerId,
        String name,
        String currency,
        BudgetPeriodType periodType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalPlanned,
        List<BudgetLineResponse> lines,
        BudgetStatus status,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
                budget.getId(), budget.getOwnerId(), budget.getName(), budget.getCurrency(),
                budget.getPeriodType(), budget.getStartDate(), budget.getEndDate(), budget.getTotalPlanned(),
                budget.getLines().stream().map(BudgetLineResponse::from).toList(),
                budget.getStatus(), budget.getArchivedAt(), budget.getVersion(),
                budget.getCreatedAt(), budget.getUpdatedAt()
        );
    }
}
