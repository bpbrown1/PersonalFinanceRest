package com.personalfinance.personfinancerest.recurringexpense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseResponse(
        UUID id,
        UUID ownerId,
        String name,
        BigDecimal amount,
        String currency,
        UUID categoryId,
        UUID accountId,
        LocalDate anchorDate,
        LocalDate endDate,
        int intervalMonths,
        RecurringExpenseStatus status,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    static RecurringExpenseResponse from(RecurringExpense expense) {
        return new RecurringExpenseResponse(
                expense.getId(), expense.getOwnerId(), expense.getName(), expense.getAmount(),
                expense.getCurrency(), expense.getCategoryId(), expense.getAccountId(),
                expense.getAnchorDate(), expense.getEndDate(), expense.getIntervalMonths(),
                expense.getStatus(), expense.getArchivedAt(), expense.getVersion(),
                expense.getCreatedAt(), expense.getUpdatedAt()
        );
    }
}
