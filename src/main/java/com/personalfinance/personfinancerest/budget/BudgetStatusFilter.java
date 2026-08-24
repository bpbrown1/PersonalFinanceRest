package com.personalfinance.personfinancerest.budget;

import java.util.Locale;

enum BudgetStatusFilter {
    ACTIVE,
    ARCHIVED,
    ALL;

    static BudgetStatusFilter fromValue(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidBudgetStatusException(value);
        }
    }
}
