package com.personalfinance.personfinancerest.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TransactionProvenance {
    MANUAL,
    IMPORTED,
    RECONCILIATION;

    @JsonCreator
    public static TransactionProvenance fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
