package com.personalfinance.personfinancerest.category;

import java.util.Locale;

enum CategoryStatusFilter {
    ACTIVE,
    ARCHIVED,
    ALL;

    static CategoryStatusFilter fromValue(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidCategoryStatusException(value);
        }
    }
}
