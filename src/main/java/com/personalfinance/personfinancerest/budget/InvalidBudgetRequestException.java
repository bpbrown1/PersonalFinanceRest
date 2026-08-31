package com.personalfinance.personfinancerest.budget;

import java.util.Map;

public class InvalidBudgetRequestException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    InvalidBudgetRequestException(Map<String, String> fieldErrors) {
        super("Budget request is invalid");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
