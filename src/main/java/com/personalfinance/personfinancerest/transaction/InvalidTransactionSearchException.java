package com.personalfinance.personfinancerest.transaction;

public class InvalidTransactionSearchException extends RuntimeException {

    private final String field;

    InvalidTransactionSearchException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
