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
        BigDecimal committed,
        BigDecimal scheduledTarget,
        BigDecimal outstandingScheduledTarget,
        BigDecimal totalBudgeted,
        BigDecimal remainingAfterCommitments,
        boolean underfunded,
        BigDecimal flexibleActual,
        BigDecimal billActual,
        BigDecimal budgetedActual,
        BigDecimal unbudgetedActual,
        BigDecimal totalActual,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        BigDecimal percentSpent,
        BigDecimal projectedUsage,
        BigDecimal projectedRemaining,
        BigDecimal projectedPercentage,
        List<BudgetLineProgressResponse> lines,
        List<BudgetProgressComponentResponse> components,
        List<UnbudgetedProgressResponse> unbudgeted,
        List<UnbudgetedCommitmentResponse> unbudgetedCommitments,
        BudgetProgressDrillDown drillDown
) {
}
