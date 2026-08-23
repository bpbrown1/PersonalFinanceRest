package com.personalfinance.personfinancerest.account.balance;

import com.personalfinance.personfinancerest.account.management.AccountStatus;
import com.personalfinance.personfinancerest.account.management.AccountType;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountBalanceServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private BalanceSnapshotRepository snapshotRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private AccountBalanceService service;
    private UUID ownerId;
    private UUID accountId;
    private FinancialAccount account;

    @BeforeEach
    void setUp() {
        service = new AccountBalanceService(accountRepository, snapshotRepository, currentUserProvider);
        ownerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        account = new FinancialAccount(
                accountId,
                ownerId,
                "Everyday Checking",
                AccountType.CHECKING,
                "USD",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("1250.75")
        );
    }

    @Test
    void recordsAnAppendOnlySnapshotAndUpdatesCurrentBalanceFromTheLatestSnapshot() {
        Instant effectiveAt = Instant.parse("2026-08-21T12:00:00Z");
        BalanceSnapshot latest = snapshot(new BigDecimal("1500.00"), effectiveAt);
        givenOwnedAccount();
        given(snapshotRepository.existsByAccountIdAndEffectiveAt(accountId, effectiveAt)).willReturn(false);
        given(snapshotRepository.saveAndFlush(any(BalanceSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(snapshotRepository.findFirstByAccountIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                any(UUID.class), any(Instant.class))).willReturn(Optional.of(latest));
        given(accountRepository.saveAndFlush(account)).willReturn(account);

        BalanceSnapshotResponse response = service.create(
                accountId,
                new CreateBalanceSnapshotRequest(new BigDecimal("1500"), effectiveAt)
        );

        assertThat(response.balance()).isEqualByComparingTo("1500.00");
        assertThat(response.source()).isEqualTo(BalanceSnapshotSource.MANUAL);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void aBackdatedSnapshotDoesNotReplaceTheLatestCurrentBalance() {
        Instant backdatedAt = Instant.parse("2026-08-20T12:00:00Z");
        BalanceSnapshot latest = snapshot(
                new BigDecimal("1800.00"),
                Instant.parse("2026-08-21T12:00:00Z")
        );
        givenOwnedAccount();
        given(snapshotRepository.saveAndFlush(any(BalanceSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(snapshotRepository.findFirstByAccountIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                any(UUID.class), any(Instant.class))).willReturn(Optional.of(latest));
        given(accountRepository.saveAndFlush(account)).willReturn(account);

        service.create(
                accountId,
                new CreateBalanceSnapshotRequest(new BigDecimal("1300.00"), backdatedAt)
        );

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1800.00");
    }

    @Test
    void rejectsDuplicateEffectiveTimestampsWithoutSaving() {
        Instant effectiveAt = Instant.parse("2026-08-21T12:00:00Z");
        givenOwnedAccount();
        given(snapshotRepository.existsByAccountIdAndEffectiveAt(accountId, effectiveAt)).willReturn(true);

        assertThatThrownBy(() -> service.create(
                accountId,
                new CreateBalanceSnapshotRequest(new BigDecimal("1500.00"), effectiveAt)
        )).isInstanceOf(BalanceSnapshotConflictException.class);

        verify(snapshotRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNewSnapshotsForAnArchivedAccount() {
        account = mock(FinancialAccount.class);
        given(account.getStatus()).willReturn(AccountStatus.ARCHIVED);
        givenOwnedAccount();

        assertThatThrownBy(() -> service.create(
                accountId,
                new CreateBalanceSnapshotRequest(
                        new BigDecimal("1500.00"),
                        Instant.parse("2026-08-21T12:00:00Z")
                )
        )).isInstanceOf(ArchivedFinancialAccountException.class);

        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    void retrievesTheLatestSnapshotAtOrBeforeTheRequestedInstant() {
        Instant asOf = Instant.parse("2026-08-21T18:00:00Z");
        BalanceSnapshot snapshot = snapshot(
                new BigDecimal("1500.00"),
                Instant.parse("2026-08-21T12:00:00Z")
        );
        givenOwnedAccount();
        given(snapshotRepository.findFirstByAccountIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                accountId, asOf)).willReturn(Optional.of(snapshot));

        AccountBalanceResponse response = service.findAsOf(accountId, asOf);

        assertThat(response.balance()).isEqualByComparingTo("1500.00");
        assertThat(response.effectiveAt()).isEqualTo(snapshot.getEffectiveAt());
    }

    @Test
    void reportsNoBalanceBeforeTheFirstSnapshot() {
        Instant asOf = Instant.parse("2026-08-19T23:59:59Z");
        givenOwnedAccount();
        given(snapshotRepository.findFirstByAccountIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                accountId, asOf)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAsOf(accountId, asOf))
                .isInstanceOf(AccountBalanceNotFoundException.class);
    }

    private void givenOwnedAccount() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(accountRepository.findByIdAndOwnerId(accountId, ownerId)).willReturn(Optional.of(account));
    }

    private BalanceSnapshot snapshot(BigDecimal balance, Instant effectiveAt) {
        return new BalanceSnapshot(
                UUID.randomUUID(),
                accountId,
                balance,
                effectiveAt,
                BalanceSnapshotSource.MANUAL
        );
    }
}
