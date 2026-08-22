package com.personalfinance.personfinancerest.account;

import java.util.UUID;

public class FinancialAccountNotFoundException extends RuntimeException {

    public FinancialAccountNotFoundException(UUID accountId) {
        super("Financial account not found: " + accountId);
    }
}
