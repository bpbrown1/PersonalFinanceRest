package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTypeTest {

    @Test
    void derivesUnambiguousSignedBalanceImpacts() {
        BigDecimal amount = new BigDecimal("25.00");

        assertThat(TransactionType.INCOME.balanceImpact(amount)).isEqualByComparingTo("25.00");
        assertThat(TransactionType.EXPENSE.balanceImpact(amount)).isEqualByComparingTo("-25.00");
        assertThat(TransactionType.TRANSFER_OUT.balanceImpact(amount)).isEqualByComparingTo("-25.00");
        assertThat(TransactionType.TRANSFER_IN.balanceImpact(amount)).isEqualByComparingTo("25.00");
        assertThat(TransactionType.fromValue(" income ")).isEqualTo(TransactionType.INCOME);
        assertThat(TransactionType.EXPENSE.toValue()).isEqualTo("expense");
    }
}
