package com.personalfinance.personfinancerest.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId,
        BigDecimal balance,
        Instant effectiveAt,
        BalanceSnapshotSource source
) {

    static AccountBalanceResponse from(BalanceSnapshot snapshot) {
        return new AccountBalanceResponse(
                snapshot.getAccountId(),
                snapshot.getBalance(),
                snapshot.getEffectiveAt(),
                snapshot.getSource()
        );
    }
}
