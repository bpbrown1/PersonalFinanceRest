package com.personalfinance.personfinancerest.account;

import java.time.Instant;
import java.util.UUID;

public class AccountBalanceNotFoundException extends RuntimeException {

    public AccountBalanceNotFoundException(UUID accountId, Instant asOf) {
        super("Account balance not found for " + accountId + " as of " + asOf);
    }
}
