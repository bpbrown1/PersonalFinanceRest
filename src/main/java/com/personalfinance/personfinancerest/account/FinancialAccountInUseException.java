package com.personalfinance.personfinancerest.account;

import java.util.UUID;

public class FinancialAccountInUseException extends RuntimeException {

    public FinancialAccountInUseException(UUID accountId) {
        super("Financial terms cannot be changed after account activity is recorded: " + accountId);
    }
}
