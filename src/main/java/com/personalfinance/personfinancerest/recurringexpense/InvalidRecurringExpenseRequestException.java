package com.personalfinance.personfinancerest.recurringexpense;

import java.util.Map;

public class InvalidRecurringExpenseRequestException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public InvalidRecurringExpenseRequestException(Map<String, String> fieldErrors) {
        super("Recurring expense request is invalid");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
