package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.transaction.TransactionPageResponse;

import java.util.Set;
import java.util.UUID;

/**
 * Pages ledger transactions selected by the budget-progress allocation rules.
 */
public interface BudgetProgressTransactionPageSource {

    TransactionPageResponse findPage(
            UUID ownerId,
            Set<UUID> transactionIds,
            int page,
            int size,
            String sort,
            String direction
    );
}
