package com.personalfinance.personfinancerest.recurringexpense;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseOccurrenceSelection(
        @NotNull UUID recurringExpenseId,
        @NotNull LocalDate dueDate
) {
}
