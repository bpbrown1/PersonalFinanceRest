package com.personalfinance.personfinancerest.recurringexpense;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseOccurrenceReference(
        String occurrenceKey,
        UUID recurringExpenseId,
        LocalDate dueDate
) {
    static RecurringExpenseOccurrenceReference from(RecurringExpenseMatch match) {
        return new RecurringExpenseOccurrenceReference(
                match.getRecurringExpenseId() + ":" + match.getDueDate(),
                match.getRecurringExpenseId(), match.getDueDate()
        );
    }
}
