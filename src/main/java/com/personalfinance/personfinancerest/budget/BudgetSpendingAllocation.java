package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetSpendingAllocation(
        UUID transactionId,
        UUID accountId,
        UUID categoryId,
        BigDecimal amount
) {
}
