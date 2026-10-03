package com.personalfinance.personfinancerest.account.reconciliation;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationConfirmRequest(
        @NotNull LocalDate statementDate,
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal statementBalance,
        @NotBlank @Size(max = 64) String ledgerToken,
        UUID categoryId,
        @Size(max = 2000) String explanation
) {
}
