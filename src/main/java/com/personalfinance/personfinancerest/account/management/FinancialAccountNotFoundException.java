package com.personalfinance.personfinancerest.account.management;

import java.util.UUID;

public class FinancialAccountNotFoundException extends RuntimeException {

    public FinancialAccountNotFoundException(UUID accountId) {
        super("Financial account not found: " + accountId);
    }
}
