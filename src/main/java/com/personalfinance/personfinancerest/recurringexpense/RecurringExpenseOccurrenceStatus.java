package com.personalfinance.personfinancerest.recurringexpense;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum RecurringExpenseOccurrenceStatus {
    OUTSTANDING,
    SATISFIED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
