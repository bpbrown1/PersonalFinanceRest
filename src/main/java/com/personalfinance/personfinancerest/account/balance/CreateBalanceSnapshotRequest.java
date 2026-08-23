package com.personalfinance.personfinancerest.account.balance;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateBalanceSnapshotRequest(
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal balance,
        @NotNull @PastOrPresent Instant effectiveAt
) {
}
