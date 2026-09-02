package com.personalfinance.personfinancerest.recurringexpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseLinkedTransactionResponse(
        UUID id,
        UUID accountId,
        UUID categoryId,
        LocalDate transactionDate,
        BigDecimal amount,
        String description,
        boolean active
) {
    static RecurringExpenseLinkedTransactionResponse from(RecurringExpenseTransactionCandidate transaction) {
        return new RecurringExpenseLinkedTransactionResponse(
                transaction.id(), transaction.accountId(), transaction.categoryId(),
                transaction.transactionDate(), transaction.amount(), transaction.description(),
                transaction.active()
        );
    }
}
