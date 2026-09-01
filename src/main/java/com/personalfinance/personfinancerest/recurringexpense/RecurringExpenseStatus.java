package com.personalfinance.personfinancerest.recurringexpense;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum RecurringExpenseStatus {
    ACTIVE,
    ARCHIVED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
