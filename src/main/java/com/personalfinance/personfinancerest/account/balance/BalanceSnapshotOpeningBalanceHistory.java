package com.personalfinance.personfinancerest.account.balance;

import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.OpeningBalanceHistory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
class BalanceSnapshotOpeningBalanceHistory implements OpeningBalanceHistory {

    private final BalanceSnapshotRepository repository;

    BalanceSnapshotOpeningBalanceHistory(BalanceSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public void createFor(FinancialAccount account) {
        repository.saveAndFlush(new BalanceSnapshot(
                UUID.randomUUID(),
                account.getId(),
                account.getOpeningBalance(),
                effectiveAt(account.getOpeningDate()),
                BalanceSnapshotSource.OPENING
        ));
    }

    @Override
    public void updateFor(FinancialAccount account) {
        BalanceSnapshot openingSnapshot = repository
                .findByAccountIdAndSource(account.getId(), BalanceSnapshotSource.OPENING)
                .orElseGet(() -> new BalanceSnapshot(
                        UUID.randomUUID(),
                        account.getId(),
                        account.getOpeningBalance(),
                        effectiveAt(account.getOpeningDate()),
                        BalanceSnapshotSource.OPENING
                ));
        openingSnapshot.updateOpeningValues(
                account.getOpeningBalance(),
                effectiveAt(account.getOpeningDate())
        );
        repository.saveAndFlush(openingSnapshot);
    }

    private Instant effectiveAt(LocalDate openingDate) {
        return openingDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
