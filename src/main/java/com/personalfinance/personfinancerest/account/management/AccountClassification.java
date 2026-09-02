package com.personalfinance.personfinancerest.account.management;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AccountClassification {
    ASSET,
    LIABILITY;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
