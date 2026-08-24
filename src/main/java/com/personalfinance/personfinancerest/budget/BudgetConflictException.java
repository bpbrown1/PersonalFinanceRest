package com.personalfinance.personfinancerest.budget;

public class BudgetConflictException extends RuntimeException {

    BudgetConflictException(String message) {
        super(message);
    }
}
