package com.personalfinance.personfinancerest.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyValuesTest {

    @Test
    void normalizesAmountsAndCurrencyCodes() {
        assertThat(MoneyValues.amountOrZero(null)).isEqualByComparingTo("0.00");
        assertThat(MoneyValues.amountOrZero(new BigDecimal("12.5"))).isEqualByComparingTo("12.50");
        assertThat(MoneyValues.currencyCode("usd")).isEqualTo("USD");
    }

    @Test
    void refusesToSilentlyRoundAnAmount() {
        assertThatThrownBy(() -> MoneyValues.amountOrZero(new BigDecimal("1.234")))
                .isInstanceOf(ArithmeticException.class);
    }
}
