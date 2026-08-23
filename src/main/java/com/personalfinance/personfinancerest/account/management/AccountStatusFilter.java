package com.personalfinance.personfinancerest.account.management;

import java.util.Locale;

enum AccountStatusFilter {
    ACTIVE,
    ARCHIVED,
    ALL;

    static AccountStatusFilter fromValue(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidAccountStatusException(value);
        }
    }
}
