package com.personalfinance.personfinancerest.transaction;

import java.util.Map;

public class InvalidTransactionAllocationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    InvalidTransactionAllocationException(Map<String, String> fieldErrors) {
        super("Transaction allocation is invalid");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
