package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BudgetProgressResponse(
        UUID budgetId,
        UUID ownerId,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        UUID accountId,
        UUID categoryId,
        BigDecimal planned,
        BigDecimal budgetedActual,
        BigDecimal unbudgetedActual,
        BigDecimal totalActual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        List<BudgetLineProgressResponse> lines,
        List<UnbudgetedProgressResponse> unbudgeted,
        BudgetProgressDrillDown drillDown
) {
}
