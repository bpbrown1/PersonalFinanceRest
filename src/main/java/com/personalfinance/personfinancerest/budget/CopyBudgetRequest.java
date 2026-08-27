package com.personalfinance.personfinancerest.budget;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.YearMonth;

public record CopyBudgetRequest(
        @NotBlank
        @Pattern(regexp = "(?!0000)[0-9]{4}-(0[1-9]|1[0-2])",
                message = "must be a calendar month in YYYY-MM format (years 0001 through 9999)")
        String targetMonth
) {
    YearMonth month() {
        return YearMonth.parse(targetMonth);
    }
}
