package com.personalfinance.personfinancerest.transaction;

import java.util.Locale;

enum TransactionStatusFilter {
    ACTIVE,
    DELETED,
    ALL;

    static TransactionStatusFilter fromValue(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidTransactionStatusException(value);
        }
    }
}
