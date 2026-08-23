package com.personalfinance.personfinancerest.account;

import java.util.UUID;

public class BalanceSnapshotNotFoundException extends RuntimeException {

    public BalanceSnapshotNotFoundException(UUID snapshotId) {
        super("Balance snapshot not found: " + snapshotId);
    }
}
