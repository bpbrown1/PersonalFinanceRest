package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.shared.validation.NonZero;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionSplitRequest(
        UUID id,
        @NotNull UUID categoryId,
        @NotNull @NonZero @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
