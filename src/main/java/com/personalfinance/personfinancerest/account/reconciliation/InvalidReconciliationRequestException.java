package com.personalfinance.personfinancerest.account.reconciliation;

import java.util.Map;

public class InvalidReconciliationRequestException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    InvalidReconciliationRequestException(Map<String, String> fieldErrors) {
        super("Invalid reconciliation request");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
