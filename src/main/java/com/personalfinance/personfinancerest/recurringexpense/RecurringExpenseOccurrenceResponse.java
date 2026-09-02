package com.personalfinance.personfinancerest.recurringexpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseOccurrenceResponse(
        String occurrenceKey,
        UUID recurringExpenseId,
        String name,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal targetAmount,
        BigDecimal actualAmount,
        BigDecimal variance,
        RecurringExpenseOccurrenceStatus status,
        RecurringExpenseLinkedTransactionResponse linkedTransaction,
        String currency,
        UUID categoryId,
        UUID accountId
) {
}
