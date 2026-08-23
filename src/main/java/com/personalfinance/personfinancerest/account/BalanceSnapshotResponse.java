package com.personalfinance.personfinancerest.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceSnapshotResponse(
        UUID id,
        UUID accountId,
        BigDecimal balance,
        Instant effectiveAt,
        BalanceSnapshotSource source,
        Instant createdAt
) {

    static BalanceSnapshotResponse from(BalanceSnapshot snapshot) {
        return new BalanceSnapshotResponse(
                snapshot.getId(),
                snapshot.getAccountId(),
                snapshot.getBalance(),
                snapshot.getEffectiveAt(),
                snapshot.getSource(),
                snapshot.getCreatedAt()
        );
    }
}
