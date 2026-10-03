package com.personalfinance.personfinancerest.account.reconciliation;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationPreviewRequest(
        @NotNull LocalDate statementDate,
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal statementBalance
) {
}
