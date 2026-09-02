package com.personalfinance.personfinancerest.budget;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum BudgetComponentStatus {
    OUTSTANDING,
    SATISFIED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
