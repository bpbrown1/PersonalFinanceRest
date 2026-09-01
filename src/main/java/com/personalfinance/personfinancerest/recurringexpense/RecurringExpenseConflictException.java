package com.personalfinance.personfinancerest.recurringexpense;

public class RecurringExpenseConflictException extends RuntimeException {
    public RecurringExpenseConflictException(String message) {
        super(message);
    }
}
