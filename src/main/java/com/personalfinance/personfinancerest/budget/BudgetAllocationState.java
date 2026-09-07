package com.personalfinance.personfinancerest.budget;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum BudgetAllocationState {
    ALLOCATED,
    COVERED_BY_ANCESTOR,
    UNBUDGETED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
