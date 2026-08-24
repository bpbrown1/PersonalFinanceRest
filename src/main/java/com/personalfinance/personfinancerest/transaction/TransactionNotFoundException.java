package com.personalfinance.personfinancerest.transaction;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Financial transaction not found: " + transactionId);
    }
}
