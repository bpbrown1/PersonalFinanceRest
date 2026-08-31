package com.personalfinance.personfinancerest.budget;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetStatusFilterTest {

    @ParameterizedTest
    @CsvSource({"active,ACTIVE", "ARCHIVED,ARCHIVED", " all ,ALL"})
    void parsesSupportedValues(String input, BudgetStatusFilter expected) {
        assertThat(BudgetStatusFilter.fromValue(input)).isEqualTo(expected);
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThatThrownBy(() -> BudgetStatusFilter.fromValue("closed"))
                .isInstanceOf(InvalidBudgetStatusException.class);
    }
}
