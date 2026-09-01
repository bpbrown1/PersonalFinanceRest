package com.personalfinance.personfinancerest.recurringexpense;

import java.util.UUID;

public class RecurringExpenseNotFoundException extends RuntimeException {
    public RecurringExpenseNotFoundException(UUID id) {
        super("Recurring expense not found: " + id);
    }
}
