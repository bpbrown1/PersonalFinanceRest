package com.personalfinance.personfinancerest.category;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum CategoryStatus {
    ACTIVE,
    ARCHIVED;

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
