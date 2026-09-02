package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetProgressComponentResponse(
        String componentKey,
        BudgetComponentSource source,
        UUID lineId,
        String occurrenceKey,
        UUID recurringExpenseId,
        UUID categoryId,
        Integer position,
        String name,
        LocalDate dueDate,
        BigDecimal target,
        BigDecimal actual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        BigDecimal projectedUsage,
        BigDecimal projectedRemaining,
        BigDecimal projectedPercentage,
        BudgetComponentStatus status,
        BigDecimal variance,
        UUID linkedTransactionId,
        BudgetProgressDrillDown drillDown
) {
}
