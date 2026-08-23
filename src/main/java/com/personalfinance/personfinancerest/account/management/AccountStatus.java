package com.personalfinance.personfinancerest.account.management;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AccountStatus {
    ACTIVE,
    ARCHIVED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
