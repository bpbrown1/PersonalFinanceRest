package com.personalfinance.personfinancerest.transaction;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TransactionStatus {
    ACTIVE,
    DELETED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
