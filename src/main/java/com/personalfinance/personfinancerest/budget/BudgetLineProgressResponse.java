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
        BigDecimal scheduledTarget,
        BigDecimal outstandingScheduledTarget,
        BigDecimal totalBudgeted,
        BigDecimal remainingAfterCommitments,
        boolean underfunded,
        List<BudgetScheduledCommitment> scheduledCommitments,
        BigDecimal actual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        BigDecimal percentSpent,
        BigDecimal projectedUsage,
        BigDecimal projectedRemaining,
        BigDecimal projectedPercentage,
        BudgetProgressDrillDown drillDown
) {
}
