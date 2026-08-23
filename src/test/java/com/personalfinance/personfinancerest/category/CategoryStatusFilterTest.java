package com.personalfinance.personfinancerest.category;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryStatusFilterTest {

    @ParameterizedTest
    @CsvSource({"active, ACTIVE", " ARCHIVED , ARCHIVED", "All, ALL"})
    void parsesSupportedValues(String value, CategoryStatusFilter expected) {
        assertThat(CategoryStatusFilter.fromValue(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "deleted"})
    void rejectsUnsupportedValues(String value) {
        assertThatThrownBy(() -> CategoryStatusFilter.fromValue(value))
                .isInstanceOf(InvalidCategoryStatusException.class)
                .hasMessageContaining("active, archived, all");
    }
}
