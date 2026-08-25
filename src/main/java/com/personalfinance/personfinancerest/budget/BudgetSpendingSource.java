package com.personalfinance.personfinancerest.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BudgetSpendingSource {

    List<BudgetSpendingAllocation> findExpenseAllocations(
            UUID ownerId, String currency, LocalDate from, LocalDate to, UUID accountId
    );
}
