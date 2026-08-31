package com.personalfinance.personfinancerest.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetLineRequest(
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal plannedAmount
) {
}
