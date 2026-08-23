package com.personalfinance.personfinancerest.account.management;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AccountType {
    CHECKING,
    SAVINGS,
    CASH,
    CREDIT_CARD,
    LOAN;

    @JsonCreator
    public static AccountType fromValue(String value) {
        return AccountType.valueOf(value.trim().replace(' ', '_').toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
