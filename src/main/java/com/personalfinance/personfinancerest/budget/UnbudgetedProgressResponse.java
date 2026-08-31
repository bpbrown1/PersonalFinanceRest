package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.util.UUID;

public record UnbudgetedProgressResponse(
        UUID categoryId,
        BigDecimal actual,
        BudgetProgressDrillDown drillDown
) {
}
