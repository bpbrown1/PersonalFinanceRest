package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.account.management.AccountStatus;
import com.personalfinance.personfinancerest.account.management.AccountType;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.CategoryApplicability;
import com.personalfinance.personfinancerest.category.CategoryStatus;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

    @Mock
    private FinancialTransactionRepository repository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private TransactionCategoryRepository categoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private FinancialTransactionService service;
    private UUID ownerId;
    private UUID accountId;
    private UUID transactionId;
    private FinancialAccount account;
    private FinancialTransaction transaction;
    private LocalDate transactionDate;

    @BeforeEach
    void setUp() {
        service = new FinancialTransactionService(
                repository, accountRepository, categoryRepository, currentUserProvider
        );
        ownerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        transactionDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        account = account(accountId, "100.00");
        transaction = transaction(transactionId, accountId, "10.00", TransactionType.EXPENSE);
    }

    @Test
    void createsIncomeAndAppliesItsPositiveImpact() {
        givenOwnerAndLockedAccount(account);
        given(repository.saveAndFlush(any(FinancialTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(createRequest("25.00", TransactionType.INCOME, null));

        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.balanceImpact()).isEqualByComparingTo("25.00");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("125.00");
        verify(accountRepository).saveAndFlush(account);
    }

    @Test
    void createsExpenseAndAppliesItsNegativeImpact() {
        givenOwnerAndLockedAccount(account);
        given(repository.saveAndFlush(any(FinancialTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(createRequest("25.00", TransactionType.EXPENSE, null));

        assertThat(response.balanceImpact()).isEqualByComparingTo("-25.00");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void updatesTheSameAccountByOnlyTheImpactDifference() {
        account.recordCurrentBalance(new BigDecimal("90.00"));
        givenOwnedTransaction();
        givenLockedAccount(account);
        given(repository.saveAndFlush(transaction)).willReturn(transaction);

        TransactionResponse response = service.update(
                transactionId, updateRequest(accountId, "20.00", TransactionType.INCOME, null)
        );

        assertThat(response.balanceImpact()).isEqualByComparingTo("20.00");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("120.00");
    }

    @Test
    void movingATransactionReversesTheOldAccountAndAppliesTheNewAccount() {
        account.recordCurrentBalance(new BigDecimal("90.00"));
        UUID newAccountId = UUID.randomUUID();
        FinancialAccount newAccount = account(newAccountId, "200.00");
        givenOwnedTransaction();
        given(accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId)).willReturn(Optional.of(account));
        given(accountRepository.findByIdAndOwnerIdForUpdate(newAccountId, ownerId)).willReturn(Optional.of(newAccount));
        given(repository.saveAndFlush(transaction)).willReturn(transaction);

        service.update(transactionId, updateRequest(newAccountId, "15.00", TransactionType.EXPENSE, null));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(newAccount.getCurrentBalance()).isEqualByComparingTo("185.00");
    }

    @Test
    void editingADeletedTransactionDoesNotChangeBalancesUntilRestore() {
        transaction.softDelete(java.time.Instant.now());
        givenOwnedTransaction();
        givenLockedAccount(account);
        given(repository.saveAndFlush(transaction)).willReturn(transaction);

        service.update(transactionId, updateRequest(accountId, "50.00", TransactionType.EXPENSE, null));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void softDeleteAndRestoreReverseAndReapplyExactlyOnce() {
        account.recordCurrentBalance(new BigDecimal("90.00"));
        givenOwnedTransaction();
        givenLockedAccount(account);
        given(repository.saveAndFlush(transaction)).willReturn(transaction);

        assertThat(service.delete(transactionId).status()).isEqualTo(TransactionStatus.DELETED);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
        service.delete(transactionId);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");

        assertThat(service.restore(transactionId).status()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("90.00");
        service.restore(transactionId);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void validatesCategoryApplicabilityAndActiveStateWhenAssigned() {
        UUID categoryId = UUID.randomUUID();
        TransactionCategory category = mock(TransactionCategory.class);
        given(category.getStatus()).willReturn(CategoryStatus.ACTIVE);
        given(category.getApplicability()).willReturn(CategoryApplicability.INCOME);
        given(categoryRepository.findByIdAndOwnerId(categoryId, ownerId)).willReturn(Optional.of(category));
        givenOwnerAndLockedAccount(account);

        assertThatThrownBy(() -> service.create(
                createRequest("10.00", TransactionType.EXPENSE, categoryId)
        )).isInstanceOf(TransactionConflictException.class).hasMessageContaining("incompatible");

        given(category.getStatus()).willReturn(CategoryStatus.ARCHIVED);
        assertThatThrownBy(() -> service.create(
                createRequest("10.00", TransactionType.EXPENSE, categoryId)
        )).isInstanceOf(TransactionConflictException.class).hasMessageContaining("archived category");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsArchivedAccountsAndOutOfRangeDates() {
        FinancialAccount archived = mock(FinancialAccount.class);
        given(archived.getStatus()).willReturn(AccountStatus.ARCHIVED);
        given(archived.getId()).willReturn(accountId);
        givenOwnerAndLockedAccount(archived);

        assertThatThrownBy(() -> service.create(createRequest("10.00", TransactionType.EXPENSE, null)))
                .isInstanceOf(TransactionConflictException.class).hasMessageContaining("archived financial account");

        givenOwnerAndLockedAccount(account);
        CreateTransactionRequest beforeOpening = new CreateTransactionRequest(
                accountId, new BigDecimal("10.00"), account.getOpeningDate().minusDays(1),
                "Invalid", TransactionType.EXPENSE, null, null, null, null
        );
        assertThatThrownBy(() -> service.create(beforeOpening))
                .isInstanceOf(TransactionConflictException.class).hasMessageContaining("opening date");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsAnOwnerScopedPageWithRequestedMetadata() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(transaction)));
        TransactionSearchCriteria criteria = TransactionSearchCriteria.from(
                "active", null, null, null, null, null, null, null,
                null, 0, 25, "date", "desc"
        );

        TransactionPageResponse response = service.search(criteria);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.sortBy()).isEqualTo("date");
        assertThat(response.sortDirection()).isEqualTo("desc");
    }

    @Test
    void summarizesActiveTransactionsByCurrencyWithFixedDecimalTotals() {
        TransactionSummaryAggregate aggregate = mock(TransactionSummaryAggregate.class);
        given(aggregate.getCurrency()).willReturn("USD");
        given(aggregate.getIncome()).willReturn(new BigDecimal("2416.00"));
        given(aggregate.getSpending()).willReturn(new BigDecimal("24.36"));
        given(aggregate.getTransactionCount()).willReturn(2L);
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.summarize(
                ownerId, transactionDate.minusDays(1), transactionDate,
                TransactionType.INCOME, TransactionType.EXPENSE, null, null, null
        )).willReturn(List.of(aggregate));

        TransactionSummaryResponse response = service.summarize(
                transactionDate.minusDays(1), transactionDate
        ).getFirst();

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.income()).isEqualByComparingTo("2416.00");
        assertThat(response.spending()).isEqualByComparingTo("24.36");
        assertThat(response.netImpact()).isEqualByComparingTo("2391.64");
        assertThat(response.transactionCount()).isEqualTo(2L);
    }

    @Test
    void rejectsAnInvertedSummaryDateRangeBeforeQueryingTheRepository() {
        assertThatThrownBy(() -> service.summarize(transactionDate, transactionDate.minusDays(1)))
                .isInstanceOf(InvalidTransactionDateRangeException.class)
                .hasMessage("from must be on or before to");

        verify(repository, never()).summarize(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private void givenOwnerAndLockedAccount(FinancialAccount lockedAccount) {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId))
                .willReturn(Optional.of(lockedAccount));
    }

    private void givenLockedAccount(FinancialAccount lockedAccount) {
        given(accountRepository.findByIdAndOwnerIdForUpdate(lockedAccount.getId(), ownerId))
                .willReturn(Optional.of(lockedAccount));
    }

    private void givenOwnedTransaction() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findByIdAndOwnerId(transactionId, ownerId)).willReturn(Optional.of(transaction));
    }

    private FinancialAccount account(UUID id, String balance) {
        return new FinancialAccount(
                id, ownerId, "Checking", AccountType.CHECKING, "USD",
                LocalDate.now(ZoneOffset.UTC).minusDays(30), new BigDecimal(balance)
        );
    }

    private FinancialTransaction transaction(UUID id, UUID financialAccountId, String amount, TransactionType type) {
        return new FinancialTransaction(
                id, ownerId, financialAccountId, null, new BigDecimal(amount), type,
                transactionDate, "Groceries", null, null, null
        );
    }

    private CreateTransactionRequest createRequest(String amount, TransactionType type, UUID categoryId) {
        return new CreateTransactionRequest(
                accountId, new BigDecimal(amount), transactionDate, "Groceries", type,
                categoryId, "Market", null, null
        );
    }

    private UpdateTransactionRequest updateRequest(UUID targetAccountId, String amount,
                                                   TransactionType type, UUID categoryId) {
        return new UpdateTransactionRequest(
                targetAccountId, new BigDecimal(amount), transactionDate, "Updated", type,
                categoryId, null, null, null
        );
    }
}
