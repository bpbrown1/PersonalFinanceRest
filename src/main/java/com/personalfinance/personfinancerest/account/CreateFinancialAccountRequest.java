package com.personalfinance.personfinancerest.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFinancialAccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter currency code") String currency,
        @NotNull LocalDate openingDate,
        @Digits(integer = 17, fraction = 2) BigDecimal openingBalance
) {
}
