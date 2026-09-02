package com.personalfinance.personfinancerest.account.management;

import com.personalfinance.personfinancerest.account.currency.SupportedCurrency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        @Digits(integer = 17, fraction = 2) BigDecimal openingBalance,
        @DecimalMin("0.000000") @DecimalMax("999.999999")
        @Digits(integer = 3, fraction = 6) BigDecimal interestRate,
        InterestRateType interestRateType
) {
    public CreateFinancialAccountRequest(
            String name, AccountType type, String currency,
            LocalDate openingDate, BigDecimal openingBalance
    ) {
        this(name, type, currency, openingDate, openingBalance, null, null);
    }
}
