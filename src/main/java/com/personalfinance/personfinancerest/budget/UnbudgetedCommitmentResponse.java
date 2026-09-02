package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UnbudgetedCommitmentResponse(
        UUID categoryId,
        BigDecimal committed,
        BigDecimal scheduledTarget,
        BigDecimal outstandingScheduledTarget,
        BigDecimal totalBudgeted,
        BigDecimal billActual,
        BigDecimal actual,
        BigDecimal remaining,
        BigDecimal percentSpent,
        BigDecimal projectedUsage,
        BigDecimal projectedRemaining,
        BigDecimal projectedPercentage,
        List<BudgetScheduledCommitment> scheduledCommitments
) {
}
