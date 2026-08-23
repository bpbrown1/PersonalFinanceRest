package com.personalfinance.personfinancerest.account.management;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStatusFilterTest {

    @ParameterizedTest
    @MethodSource("validValues")
    void parsesSupportedValues(String value, AccountStatusFilter expected) {
        assertThat(AccountStatusFilter.fromValue(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "deleted", "active-only"})
    void rejectsUnsupportedValues(String value) {
        assertThatThrownBy(() -> AccountStatusFilter.fromValue(value))
                .isInstanceOf(InvalidAccountStatusException.class)
                .hasMessageContaining("active, archived, all");
    }

    private static Stream<Arguments> validValues() {
        return Stream.of(
                Arguments.of("active", AccountStatusFilter.ACTIVE),
                Arguments.of(" ARCHIVED ", AccountStatusFilter.ARCHIVED),
                Arguments.of("All", AccountStatusFilter.ALL)
        );
    }
}
