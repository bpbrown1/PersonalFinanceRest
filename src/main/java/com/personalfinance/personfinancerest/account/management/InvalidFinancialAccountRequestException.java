package com.personalfinance.personfinancerest.account.management;

import java.util.Map;

public class InvalidFinancialAccountRequestException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    InvalidFinancialAccountRequestException(Map<String, String> fieldErrors) {
        super("Financial account request is invalid");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
