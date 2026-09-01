package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BudgetLineProgressResponse(
        UUID lineId,
        UUID categoryId,
        int position,
        BigDecimal planned,
        BigDecimal committed,
        BigDecimal remainingAfterCommitments,
        boolean underfunded,
        List<BudgetScheduledCommitment> scheduledCommitments,
        BigDecimal actual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        BudgetProgressDrillDown drillDown
) {
}
