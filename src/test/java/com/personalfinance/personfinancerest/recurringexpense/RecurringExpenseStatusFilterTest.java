package com.personalfinance.personfinancerest.recurringexpense;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurringExpenseStatusFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"active", "ACTIVE", "archived", "ARCHIVED", "all", "ALL"})
    void acceptsSupportedValuesCaseInsensitively(String value) {
        assertThat(RecurringExpenseStatusFilter.fromValue(value).name())
                .isEqualTo(value.toUpperCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "deleted", "future"})
    void rejectsUnsupportedValues(String value) {
        assertThatThrownBy(() -> RecurringExpenseStatusFilter.fromValue(value))
                .isInstanceOf(InvalidRecurringExpenseStatusException.class);
    }
}
