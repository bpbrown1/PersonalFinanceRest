package com.personalfinance.personfinancerest.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Locale;

public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER_OUT,
    TRANSFER_IN;

    @JsonCreator
    public static TransactionType fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    BigDecimal balanceImpact(BigDecimal amount) {
        return switch (this) {
            case INCOME, TRANSFER_IN -> amount;
            case EXPENSE, TRANSFER_OUT -> amount.negate();
        };
    }

    boolean isTransfer() {
        return this == TRANSFER_OUT || this == TRANSFER_IN;
    }
}
