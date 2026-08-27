package com.personalfinance.personfinancerest.budget;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CopyBudgetRequestTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-00", "2026-13", "2026-2", "2026-02-01", "0000-01", "10000-01"})
    void rejectsMissingOrInvalidCalendarMonths(String value) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new CopyBudgetRequest(value)))
                    .isNotEmpty().allSatisfy(violation ->
                            assertThat(violation.getPropertyPath().toString()).isEqualTo("targetMonth"));
        }
    }

    @Test
    void derivesLeapYearAndYearBoundaryDates() {
        CopyBudgetRequest leap = new CopyBudgetRequest("2028-02");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(leap)).isEmpty();
        }
        assertThat(leap.month().atDay(1)).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(leap.month().atEndOfMonth()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(new CopyBudgetRequest("2027-02").month().atEndOfMonth())
                .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(new CopyBudgetRequest("9999-12").month().atEndOfMonth())
                .isEqualTo(LocalDate.of(9999, 12, 31));
    }
}
