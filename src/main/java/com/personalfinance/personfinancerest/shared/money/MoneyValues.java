package com.personalfinance.personfinancerest.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class MoneyValues {

    private static final int SCALE = 2;

    private MoneyValues() {
    }

    public static BigDecimal amountOrZero(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static String currencyCode(String currency) {
        return currency.toUpperCase(Locale.ROOT);
    }
}
