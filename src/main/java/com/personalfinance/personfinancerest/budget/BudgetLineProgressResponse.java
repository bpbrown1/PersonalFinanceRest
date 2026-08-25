package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetLineProgressResponse(
        UUID lineId,
        UUID categoryId,
        int position,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        BudgetProgressDrillDown drillDown
) {
}
