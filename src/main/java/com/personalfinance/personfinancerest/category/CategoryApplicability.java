package com.personalfinance.personfinancerest.category;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum CategoryApplicability {
    INCOME,
    EXPENSE,
    BOTH;

    @JsonCreator
    public static CategoryApplicability fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
