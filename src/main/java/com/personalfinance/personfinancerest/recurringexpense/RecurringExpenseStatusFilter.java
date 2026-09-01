package com.personalfinance.personfinancerest.recurringexpense;

import java.util.Locale;

enum RecurringExpenseStatusFilter {
    ACTIVE,
    ARCHIVED,
    ALL;

    static RecurringExpenseStatusFilter fromValue(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRecurringExpenseStatusException(value);
        }
    }
}
