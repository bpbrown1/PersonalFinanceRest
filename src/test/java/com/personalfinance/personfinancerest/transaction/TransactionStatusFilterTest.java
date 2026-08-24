package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStatusFilterTest {

    @ParameterizedTest
    @CsvSource({"active, ACTIVE", " DELETED , DELETED", "All, ALL"})
    void parsesSupportedValues(String value, TransactionStatusFilter expected) {
        assertThat(TransactionStatusFilter.fromValue(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "archived"})
    void rejectsUnsupportedValues(String value) {
        assertThatThrownBy(() -> TransactionStatusFilter.fromValue(value))
                .isInstanceOf(InvalidTransactionStatusException.class)
                .hasMessageContaining("active, deleted, all");
    }
}
