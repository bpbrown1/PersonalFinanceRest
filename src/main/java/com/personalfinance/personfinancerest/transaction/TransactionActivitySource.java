package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.account.activity.FinancialAccountActivitySource;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class TransactionActivitySource implements FinancialAccountActivitySource {

    private final FinancialTransactionRepository repository;

    TransactionActivitySource(FinancialTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsFor(UUID accountId) {
        return repository.existsByAccountId(accountId);
    }
}
