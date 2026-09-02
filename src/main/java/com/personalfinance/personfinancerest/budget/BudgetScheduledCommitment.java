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
        UUID accountId,
        boolean satisfied,
        BigDecimal actualAmount,
        BigDecimal variance,
        UUID linkedTransactionId,
        UUID linkedTransactionAccountId,
        LocalDate linkedTransactionDate
) {
    public BudgetScheduledCommitment(
            String occurrenceKey, UUID recurringExpenseId, String name, LocalDate dueDate,
            BigDecimal amount, String currency, UUID categoryId, UUID accountId) {
        this(occurrenceKey, recurringExpenseId, name, dueDate, amount, currency,
                categoryId, accountId, false, null, null, null, null, null);
    }

    public BudgetScheduledCommitment(
            String occurrenceKey, UUID recurringExpenseId, String name, LocalDate dueDate,
            BigDecimal amount, String currency, UUID categoryId, UUID accountId,
            boolean satisfied, BigDecimal actualAmount, BigDecimal variance,
            UUID linkedTransactionId) {
        this(occurrenceKey, recurringExpenseId, name, dueDate, amount, currency,
                categoryId, accountId, satisfied, actualAmount, variance,
                linkedTransactionId, null, null);
    }

    public BigDecimal outstandingAmount() {
        return satisfied ? BigDecimal.ZERO.setScale(2) : amount;
    }
}
