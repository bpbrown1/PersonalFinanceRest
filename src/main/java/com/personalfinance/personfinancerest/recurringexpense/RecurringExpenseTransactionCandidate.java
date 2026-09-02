package com.personalfinance.personfinancerest.recurringexpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseTransactionCandidate(
        UUID id,
        UUID ownerId,
        UUID accountId,
        UUID categoryId,
        BigDecimal amount,
        String currency,
        LocalDate transactionDate,
        String description,
        boolean expense,
        boolean active,
        boolean split
) {
}
