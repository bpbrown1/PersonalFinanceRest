package com.personalfinance.personfinancerest.budget;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum BudgetComponentSource {
    FLEXIBLE,
    RECURRING;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
