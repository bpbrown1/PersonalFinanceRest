package com.personalfinance.personfinancerest.budget;

import java.time.YearMonth;
import java.util.UUID;

public class BudgetTargetMonthConflictException extends BudgetConflictException {

    private final UUID existingBudgetId;

    BudgetTargetMonthConflictException(YearMonth month, UUID existingBudgetId) {
        super("A budget already exists for target month: " + month);
        this.existingBudgetId = existingBudgetId;
    }

    public UUID getExistingBudgetId() {
        return existingBudgetId;
    }
}
