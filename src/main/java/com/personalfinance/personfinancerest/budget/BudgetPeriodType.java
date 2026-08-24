package com.personalfinance.personfinancerest.budget;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum BudgetPeriodType {
    MONTHLY;

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
