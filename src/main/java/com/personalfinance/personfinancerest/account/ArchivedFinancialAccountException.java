package com.personalfinance.personfinancerest.account;

import java.util.UUID;

public class ArchivedFinancialAccountException extends RuntimeException {

    public ArchivedFinancialAccountException(UUID accountId) {
        super("Balance snapshots cannot be recorded for an archived account: " + accountId);
    }
}
