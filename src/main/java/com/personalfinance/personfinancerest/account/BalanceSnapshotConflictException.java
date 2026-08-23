package com.personalfinance.personfinancerest.account;

import java.time.Instant;

public class BalanceSnapshotConflictException extends RuntimeException {

    public BalanceSnapshotConflictException(Instant effectiveAt) {
        super("A balance snapshot already exists at " + effectiveAt);
    }
}
