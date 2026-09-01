package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UnbudgetedCommitmentResponse(
        UUID categoryId,
        BigDecimal committed,
        List<BudgetScheduledCommitment> scheduledCommitments
) {
}
