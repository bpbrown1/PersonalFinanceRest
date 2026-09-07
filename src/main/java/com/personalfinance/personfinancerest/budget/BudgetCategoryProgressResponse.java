package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.category.CategoryStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BudgetCategoryProgressResponse(
        UUID categoryId,
        String categoryName,
        List<BudgetCategoryPathSegment> path,
        CategoryStatus categoryStatus,
        BudgetAllocationState allocationState,
        UUID lineId,
        BigDecimal directPlanned,
        BigDecimal directScheduledTarget,
        BigDecimal directTarget,
        BigDecimal rollupTarget,
        BigDecimal directFlexibleActual,
        BigDecimal directBillActual,
        BigDecimal directActual,
        BigDecimal rollupActual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        int descendantAllocationCount,
        List<BudgetCategoryProgressResponse> children
) {
}
