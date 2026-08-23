package com.personalfinance.personfinancerest.account.balance;

import com.personalfinance.personfinancerest.account.management.AccountStatus;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.shared.money.MoneyValues;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class AccountBalanceService {

    private final FinancialAccountRepository accountRepository;
    private final BalanceSnapshotRepository snapshotRepository;
    private final CurrentUserProvider currentUserProvider;

    AccountBalanceService(FinancialAccountRepository accountRepository,
                          BalanceSnapshotRepository snapshotRepository,
                          CurrentUserProvider currentUserProvider) {
        this.accountRepository = accountRepository;
        this.snapshotRepository = snapshotRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    BalanceSnapshotResponse create(UUID accountId, CreateBalanceSnapshotRequest request) {
        FinancialAccount account = findOwnedAccount(accountId);
        if (account.getStatus() == AccountStatus.ARCHIVED) {
            throw new ArchivedFinancialAccountException(accountId);
        }
        if (snapshotRepository.existsByAccountIdAndEffectiveAt(accountId, request.effectiveAt())) {
            throw new BalanceSnapshotConflictException(request.effectiveAt());
        }

        BalanceSnapshot snapshot = snapshotRepository.saveAndFlush(new BalanceSnapshot(
                UUID.randomUUID(),
                accountId,
                MoneyValues.amountOrZero(request.balance()),
                request.effectiveAt(),
                BalanceSnapshotSource.MANUAL
        ));

        BalanceSnapshot latestSnapshot = latestSnapshot(accountId, Instant.now());
        account.recordCurrentBalance(latestSnapshot.getBalance());
        accountRepository.saveAndFlush(account);
        return BalanceSnapshotResponse.from(snapshot);
    }

    @Transactional(readOnly = true)
    List<BalanceSnapshotResponse> findAll(UUID accountId) {
        findOwnedAccount(accountId);
        return snapshotRepository.findAllByAccountIdOrderByEffectiveAtAsc(accountId).stream()
                .map(BalanceSnapshotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    BalanceSnapshotResponse findById(UUID accountId, UUID snapshotId) {
        findOwnedAccount(accountId);
        return snapshotRepository.findByIdAndAccountId(snapshotId, accountId)
                .map(BalanceSnapshotResponse::from)
                .orElseThrow(() -> new BalanceSnapshotNotFoundException(snapshotId));
    }

    @Transactional(readOnly = true)
    AccountBalanceResponse findAsOf(UUID accountId, Instant asOf) {
        findOwnedAccount(accountId);
        return AccountBalanceResponse.from(latestSnapshot(accountId, asOf));
    }

    private BalanceSnapshot latestSnapshot(UUID accountId, Instant asOf) {
        return snapshotRepository
                .findFirstByAccountIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(accountId, asOf)
                .orElseThrow(() -> new AccountBalanceNotFoundException(accountId, asOf));
    }

    private FinancialAccount findOwnedAccount(UUID accountId) {
        return accountRepository.findByIdAndOwnerId(accountId, currentUserProvider.userId())
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
    }
}
