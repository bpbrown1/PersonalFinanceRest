package com.personalfinance.personfinancerest.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetScheduledCommitment(
        String occurrenceKey,
        UUID recurringExpenseId,
        String name,
        LocalDate dueDate,
        BigDecimal amount,
        String currency,
        UUID categoryId,
        UUID accountId
) {
}
