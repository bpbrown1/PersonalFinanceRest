package com.personalfinance.personfinancerest.budget;

public class InvalidBudgetStatusException extends RuntimeException {

    public InvalidBudgetStatusException(String value) {
        super("Unsupported budget status: " + value);
    }
}
