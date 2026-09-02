package com.personalfinance.personfinancerest.account.management;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum InterestRateType {
    APR,
    APY;

    @JsonCreator
    public static InterestRateType fromValue(String value) {
        return InterestRateType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
