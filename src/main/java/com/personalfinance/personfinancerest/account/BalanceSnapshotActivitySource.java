package com.personalfinance.personfinancerest.account;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class BalanceSnapshotActivitySource implements FinancialAccountActivitySource {

    private final BalanceSnapshotRepository repository;

    BalanceSnapshotActivitySource(BalanceSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsFor(UUID accountId) {
        return repository.existsByAccountIdAndSource(accountId, BalanceSnapshotSource.MANUAL);
    }
}
