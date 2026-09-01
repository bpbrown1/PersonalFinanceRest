package com.personalfinance.personfinancerest.recurringexpense;

public class InvalidRecurringExpenseStatusException extends RuntimeException {
    InvalidRecurringExpenseStatusException(String value) {
        super("Unsupported recurring expense status: " + value);
    }
}
