package com.personalfinance.personfinancerest.transaction;

public class TransactionConflictException extends RuntimeException {

    public TransactionConflictException(String message) {
        super(message);
    }
}
