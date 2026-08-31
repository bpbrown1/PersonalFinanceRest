package com.personalfinance.personfinancerest.budget;

import java.util.UUID;

public class BudgetLineNotFoundException extends RuntimeException {

    BudgetLineNotFoundException(UUID lineId) {
        super("Budget line not found: " + lineId);
    }
}
