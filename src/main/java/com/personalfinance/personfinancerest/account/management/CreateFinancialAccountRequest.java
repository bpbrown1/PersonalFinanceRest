package com.personalfinance.personfinancerest.account.management;

import com.personalfinance.personfinancerest.account.currency.SupportedCurrency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFinancialAccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @NotBlank @SupportedCurrency String currency,
        @NotNull LocalDate openingDate,
        @Digits(integer = 17, fraction = 2) BigDecimal openingBalance
) {
}
