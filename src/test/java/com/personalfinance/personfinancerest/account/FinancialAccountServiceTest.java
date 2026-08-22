package com.personalfinance.personfinancerest.account;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

    @Mock
    private FinancialAccountRepository repository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private FinancialAccountActivity financialAccountActivity;

    private FinancialAccountService service;
    private UUID ownerId;
    private UUID accountId;
    private FinancialAccount account;

    @BeforeEach
    void setUp() {
        service = new FinancialAccountService(repository, currentUserProvider, financialAccountActivity);
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
    void createsAnAccountForTheCurrentOwnerAndNormalizesValues() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.saveAndFlush(any(FinancialAccount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FinancialAccountResponse response = service.create(new CreateFinancialAccountRequest(
                " Everyday Checking ",
                AccountType.CHECKING,
                "usd",
                LocalDate.of(2026, 8, 20),
                null
        ));

        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.name()).isEqualTo("Everyday Checking");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.openingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void selectsTheActiveAccountQuery() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAsc(ownerId))
                .willReturn(List.of(account));

        assertThat(service.findAll(AccountStatusFilter.ACTIVE)).hasSize(1);

        verify(repository).findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAsc(ownerId);
    }

    @Test
    void selectsTheArchivedAccountQuery() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findAllByOwnerIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(ownerId))
                .willReturn(List.of(account));

        assertThat(service.findAll(AccountStatusFilter.ARCHIVED)).hasSize(1);

        verify(repository).findAllByOwnerIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(ownerId);
    }

    @Test
    void selectsTheAllAccountsQuery() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId)).willReturn(List.of(account));

        assertThat(service.findAll(AccountStatusFilter.ALL)).hasSize(1);

        verify(repository).findAllByOwnerIdOrderByCreatedAtAsc(ownerId);
    }

    @Test
    void preservesUnspecifiedFieldsDuringAPartialUpdate() {
        givenOwnedAccount();
        given(repository.saveAndFlush(account)).willReturn(account);

        FinancialAccountResponse response = service.update(accountId, new UpdateFinancialAccountRequest(
                " Primary Checking ", null, null, null, null
        ));

        assertThat(response.name()).isEqualTo("Primary Checking");
        assertThat(response.type()).isEqualTo(AccountType.CHECKING);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.openingDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(response.openingBalance()).isEqualByComparingTo("1250.75");
        verifyNoInteractions(financialAccountActivity);
    }

    @Test
    void normalizesFinancialTermsBeforeSaving() {
        givenOwnedAccount();
        given(financialAccountActivity.existsFor(accountId)).willReturn(false);
        given(repository.saveAndFlush(account)).willReturn(account);

        FinancialAccountResponse response = service.update(accountId, new UpdateFinancialAccountRequest(
                null,
                null,
                "eur",
                LocalDate.of(2026, 8, 1),
                new BigDecimal("1500.5")
        ));

        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.openingDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.openingBalance()).isEqualByComparingTo("1500.50");
    }

    @Test
    void rejectsFinancialTermChangesAfterActivityExistsWithoutSaving() {
        givenOwnedAccount();
        given(financialAccountActivity.existsFor(accountId)).willReturn(true);

        assertThatThrownBy(() -> service.update(accountId, new UpdateFinancialAccountRequest(
                null, null, null, null, new BigDecimal("2000.00")
        )))
                .isInstanceOf(FinancialAccountInUseException.class)
                .hasMessageContaining(accountId.toString());

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void looksUpAccountsWithinTheCurrentOwnerBoundary() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findByIdAndOwnerId(accountId, ownerId)).willReturn(Optional.of(account));

        FinancialAccountResponse response = service.findById(accountId);

        assertThat(response.id()).isEqualTo(accountId);
        verify(repository).findByIdAndOwnerId(accountId, ownerId);
    }

    @Test
    void reportsAnUnknownOrUnownedAccountAsNotFound() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findByIdAndOwnerId(accountId, ownerId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(accountId))
                .isInstanceOf(FinancialAccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }

    @Test
    void archivesAndRestoresAnOwnedAccount() {
        givenOwnedAccount();
        given(repository.saveAndFlush(account)).willReturn(account);

        assertThat(service.archive(accountId).status()).isEqualTo(AccountStatus.ARCHIVED);
        assertThat(service.restore(accountId).status()).isEqualTo(AccountStatus.ACTIVE);

        verify(repository, times(2)).findByIdAndOwnerId(accountId, ownerId);
    }

    private void givenOwnedAccount() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findByIdAndOwnerId(accountId, ownerId)).willReturn(Optional.of(account));
    }
}
