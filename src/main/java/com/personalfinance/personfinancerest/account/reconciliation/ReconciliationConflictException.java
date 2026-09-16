package com.personalfinance.personfinancerest.account.reconciliation;

public class ReconciliationConflictException extends RuntimeException {

    ReconciliationConflictException(String message) {
        super(message);
    }
}
