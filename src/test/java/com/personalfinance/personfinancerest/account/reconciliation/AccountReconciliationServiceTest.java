package com.personalfinance.personfinancerest.account.reconciliation;

import com.personalfinance.personfinancerest.account.management.AccountType;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.CategoryApplicability;
import com.personalfinance.personfinancerest.category.CategoryStatus;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.transaction.FinancialTransaction;
import com.personalfinance.personfinancerest.transaction.FinancialTransactionRepository;
import com.personalfinance.personfinancerest.transaction.TransactionProvenance;
import com.personalfinance.personfinancerest.transaction.TransactionType;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountReconciliationServiceTest {

    private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 8, 31);

    @Mock
    private AccountReconciliationRepository reconciliationRepository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private TransactionCategoryRepository categoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private AccountReconciliationService service;
    private FinancialAccount account;
    private UUID ownerId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        service = new AccountReconciliationService(
                reconciliationRepository, accountRepository, transactionRepository,
                categoryRepository, currentUserProvider
        );
        ownerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        account = new FinancialAccount(
                accountId, ownerId, "Checking", AccountType.CHECKING, "USD",
                LocalDate.of(2026, 1, 1), new BigDecimal("100.00")
        );
        given(currentUserProvider.userId()).willReturn(ownerId);
    }

    @Test
    void previewUsesOpeningBalanceAndInclusiveActiveLedgerActivity() {
        FinancialTransaction income = ledgerEntry("10.25", TransactionType.INCOME);
        FinancialTransaction expense = ledgerEntry("3.10", TransactionType.EXPENSE);
        given(accountRepository.findByIdAndOwnerId(accountId, ownerId)).willReturn(Optional.of(account));
        given(transactionRepository.findActiveLedgerAsOf(ownerId, accountId, STATEMENT_DATE))
                .willReturn(List.of(income, expense));

        ReconciliationPreviewResponse response = service.preview(
                accountId, new ReconciliationPreviewRequest(STATEMENT_DATE, new BigDecimal("110.00"))
        );

        assertThat(response.calculatedBalance()).isEqualByComparingTo("107.15");
        assertThat(response.difference()).isEqualByComparingTo("2.85");
        assertThat(response.adjustmentType()).isEqualTo(TransactionType.INCOME);
        assertThat(response.transactionCount()).isEqualTo(2);
        assertThat(response.ledgerToken()).hasSize(64);
    }

    @Test
    void confirmationRejectsAStaleLedgerTokenBeforeWritingAnything() {
        given(reconciliationRepository.findByOwnerIdAndAccountIdAndIdempotencyKey(
                ownerId, accountId, "key-1")).willReturn(Optional.empty());
        given(accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId))
                .willReturn(Optional.of(account));
        given(transactionRepository.findActiveLedgerAsOf(ownerId, accountId, STATEMENT_DATE))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.confirm(
                accountId, "key-1",
                new ReconciliationConfirmRequest(
                        STATEMENT_DATE, new BigDecimal("90.00"), "stale-token",
                        UUID.randomUUID(), "Correction"
                )
        )).isInstanceOf(ReconciliationConflictException.class);

        verify(reconciliationRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmationCreatesOneProtectedExpenseAdjustmentAndRebuildsCurrentBalance() {
        given(accountRepository.findByIdAndOwnerId(accountId, ownerId)).willReturn(Optional.of(account));
        given(transactionRepository.findActiveLedgerAsOf(ownerId, accountId, STATEMENT_DATE))
                .willReturn(List.of());
        ReconciliationPreviewResponse preview = service.preview(
                accountId, new ReconciliationPreviewRequest(STATEMENT_DATE, new BigDecimal("95.00"))
        );

        UUID categoryId = UUID.randomUUID();
        TransactionCategory category = org.mockito.Mockito.mock(TransactionCategory.class);
        given(category.getId()).willReturn(categoryId);
        given(category.getStatus()).willReturn(CategoryStatus.ACTIVE);
        given(category.getApplicability()).willReturn(CategoryApplicability.EXPENSE);
        given(categoryRepository.findByIdAndOwnerId(categoryId, ownerId)).willReturn(Optional.of(category));
        given(reconciliationRepository.findByOwnerIdAndAccountIdAndIdempotencyKey(
                ownerId, accountId, "key-2")).willReturn(Optional.empty());
        given(accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId))
                .willReturn(Optional.of(account));
        given(reconciliationRepository.saveAndFlush(any(AccountReconciliation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        AtomicReference<FinancialTransaction> savedAdjustment = new AtomicReference<>();
        given(transactionRepository.saveAndFlush(any(FinancialTransaction.class)))
                .willAnswer(invocation -> {
                    FinancialTransaction transaction = invocation.getArgument(0);
                    savedAdjustment.set(transaction);
                    return transaction;
                });
        given(accountRepository.saveAndFlush(account)).willReturn(account);

        ArgumentCaptor<FinancialTransaction> adjustmentCaptor =
                ArgumentCaptor.forClass(FinancialTransaction.class);
        given(transactionRepository.findActiveLedgerAsOf(
                ownerId, accountId, LocalDate.now(java.time.ZoneOffset.UTC)
        )).willAnswer(invocation -> List.of(savedAdjustment.get()));

        ReconciliationResponse response = service.confirm(
                accountId, "key-2",
                new ReconciliationConfirmRequest(
                        STATEMENT_DATE, new BigDecimal("95.00"), preview.ledgerToken(),
                        categoryId, "Statement correction"
                )
        );

        verify(transactionRepository).saveAndFlush(adjustmentCaptor.capture());
        FinancialTransaction adjustment = adjustmentCaptor.getValue();
        assertThat(adjustment.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(adjustment.getAmount()).isEqualByComparingTo("5.00");
        assertThat(adjustment.getProvenance()).isEqualTo(TransactionProvenance.RECONCILIATION);
        assertThat(response.transactionId()).isEqualTo(adjustment.getId());
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("95.00");
    }

    private FinancialTransaction ledgerEntry(String amount, TransactionType type) {
        FinancialTransaction transaction = org.mockito.Mockito.mock(FinancialTransaction.class);
        UUID id = UUID.randomUUID();
        BigDecimal value = new BigDecimal(amount);
        given(transaction.getId()).willReturn(id);
        given(transaction.getTransactionDate()).willReturn(STATEMENT_DATE);
        given(transaction.getType()).willReturn(type);
        given(transaction.getAmount()).willReturn(value);
        given(transaction.getUpdatedAt()).willReturn(Instant.parse("2026-08-31T12:00:00Z"));
        given(transaction.balanceImpact()).willReturn(
                type == TransactionType.EXPENSE ? value.negate() : value
        );
        return transaction;
    }
}
