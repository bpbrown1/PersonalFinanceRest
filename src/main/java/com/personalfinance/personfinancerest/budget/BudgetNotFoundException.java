package com.personalfinance.personfinancerest.budget;

import java.util.UUID;

public class BudgetNotFoundException extends RuntimeException {

    BudgetNotFoundException(UUID budgetId) {
        super("Budget not found: " + budgetId);
    }
}
