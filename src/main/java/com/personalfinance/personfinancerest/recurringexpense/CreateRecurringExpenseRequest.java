package com.personalfinance.personfinancerest.recurringexpense;

import com.personalfinance.personfinancerest.account.currency.SupportedCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateRecurringExpenseRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") @SupportedCurrency String currency,
        @NotNull UUID categoryId,
        UUID accountId,
        @NotNull LocalDate anchorDate,
        LocalDate endDate,
        @Min(1) int intervalMonths
) {
}
