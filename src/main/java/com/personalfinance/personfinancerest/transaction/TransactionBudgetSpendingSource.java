package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.budget.BudgetSpendingAllocation;
import com.personalfinance.personfinancerest.budget.BudgetSpendingSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class TransactionBudgetSpendingSource implements BudgetSpendingSource {

    private final FinancialTransactionRepository repository;

    TransactionBudgetSpendingSource(FinancialTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<BudgetSpendingAllocation> findExpenseAllocations(
            UUID ownerId, String currency, LocalDate from, LocalDate to, UUID accountId
    ) {
        List<BudgetSpendingAllocation> result = new ArrayList<>();
        for (FinancialTransaction transaction : repository.findBudgetExpenses(
                ownerId, currency, from, to, accountId, TransactionType.EXPENSE
        )) {
            if (transaction.getSplits().isEmpty()) {
                result.add(new BudgetSpendingAllocation(
                        transaction.getId(), transaction.getAccountId(), transaction.getCategoryId(),
                        transaction.getAmount()
                ));
            } else {
                transaction.getSplits().forEach(split -> result.add(new BudgetSpendingAllocation(
                        transaction.getId(), transaction.getAccountId(), split.getCategoryId(), split.getAmount()
                )));
            }
        }
        return List.copyOf(result);
    }
}
