package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.budget.BudgetProgressTransactionPageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
class TransactionBudgetProgressPageSource implements BudgetProgressTransactionPageSource {

    private final FinancialTransactionRepository repository;

    TransactionBudgetProgressPageSource(FinancialTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionPageResponse findPage(
            UUID ownerId,
            Set<UUID> transactionIds,
            int page,
            int size,
            String sort,
            String direction
    ) {
        TransactionSearchCriteria criteria = TransactionSearchCriteria.from(
                "active", null, null, null, null, "expense",
                null, null, null, page, size, sort, direction
        );
        PageRequest pageRequest = PageRequest.of(page, size, criteria.pageableSort());
        Page<FinancialTransaction> result = transactionIds.isEmpty()
                ? Page.empty(pageRequest)
                : repository.findAll(
                        FinancialTransactionSpecifications.matching(ownerId, criteria)
                                .and((root, query, builder) -> root.get("id").in(transactionIds)),
                        pageRequest
                );
        return TransactionPageResponse.from(result, criteria);
    }
}
