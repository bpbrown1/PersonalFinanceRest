package com.personalfinance.personfinancerest.transaction;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private FinancialTransactionRepository repository;
    @Mock
    private FinancialAccountRepository accountRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private TransferService service;
    private UUID ownerId;
    private FinancialAccount source;
    private FinancialAccount destination;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        service = new TransferService(repository, accountRepository, currentUserProvider);
        ownerId = UUID.randomUUID();
        date = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        source = account("Source", "USD", "100.00");
        destination = account("Destination", "USD", "50.00");
    }

    @Test
    void createsLinkedLegsAndAppliesBothBalanceImpacts() {
        givenAccounts(source, destination);
        given(repository.saveAllAndFlush(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        TransferResponse response = service.create(createRequest("20.00", "20.00"));

        assertThat(response.sourceAccountId()).isEqualTo(source.getId());
        assertThat(response.destinationAccountId()).isEqualTo(destination.getId());
        assertThat(source.getCurrentBalance()).isEqualByComparingTo("80.00");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo("70.00");
        verify(accountRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void allowsExplicitDifferentAmountsForCrossCurrencyTransfers() {
        destination = account("Destination", "EUR", "50.00");
        givenAccounts(source, destination);
        given(repository.saveAllAndFlush(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        TransferResponse response = service.create(createRequest("20.00", "18.50"));

        assertThat(response.sourceAmount()).isEqualByComparingTo("20.00");
        assertThat(response.destinationAmount()).isEqualByComparingTo("18.50");
        assertThat(source.getCurrentBalance()).isEqualByComparingTo("80.00");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo("68.50");
    }

    @Test
    void rejectsIdenticalAccountsAndSameCurrencyAmountMismatchBeforeSaving() {
        given(currentUserProvider.userId()).willReturn(ownerId);

        assertThatThrownBy(() -> service.create(new CreateTransferRequest(
                source.getId(), source.getId(), new BigDecimal("10.00"), new BigDecimal("10.00"),
                date, "Invalid", null, null
        ))).isInstanceOf(TransactionConflictException.class).hasMessageContaining("different");

        givenAccounts(source, destination);
        assertThatThrownBy(() -> service.create(createRequest("20.00", "19.00")))
                .isInstanceOf(TransactionConflictException.class).hasMessageContaining("equal");
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void deleteAndRestoreReverseAndReapplyBothLegsExactlyOnce() {
        UUID transferId = UUID.randomUUID();
        List<FinancialTransaction> legs = legs(transferId);
        source.recordCurrentBalance(new BigDecimal("80.00"));
        destination.recordCurrentBalance(new BigDecimal("70.00"));
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findAllByTransferIdAndOwnerIdOrderByType(transferId, ownerId)).willReturn(legs);
        given(repository.saveAllAndFlush(legs)).willReturn(legs);
        givenAccounts(source, destination);

        assertThat(service.delete(transferId).status()).isEqualTo(TransactionStatus.DELETED);
        assertThat(source.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo("50.00");
        service.delete(transferId);
        assertThat(source.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo("50.00");

        assertThat(service.restore(transferId).status()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(source.getCurrentBalance()).isEqualByComparingTo("80.00");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo("70.00");
    }

    private void givenAccounts(FinancialAccount... accounts) {
        given(currentUserProvider.userId()).willReturn(ownerId);
        for (FinancialAccount account : accounts) {
            given(accountRepository.findByIdAndOwnerIdForUpdate(account.getId(), ownerId))
                    .willReturn(Optional.of(account));
        }
    }

    private FinancialAccount account(String name, String currency, String balance) {
        return new FinancialAccount(
                UUID.randomUUID(), ownerId, name, AccountType.CHECKING, currency,
                date.minusDays(30), new BigDecimal(balance)
        );
    }

    private CreateTransferRequest createRequest(String sourceAmount, String destinationAmount) {
        return new CreateTransferRequest(
                source.getId(), destination.getId(), new BigDecimal(sourceAmount),
                new BigDecimal(destinationAmount), date, "Transfer", "Notes", "ref-1"
        );
    }

    private List<FinancialTransaction> legs(UUID transferId) {
        return List.of(
                FinancialTransaction.transferLeg(
                        UUID.randomUUID(), transferId, ownerId, source.getId(), new BigDecimal("20.00"),
                        TransactionType.TRANSFER_OUT, date, "Transfer", null, null
                ),
                FinancialTransaction.transferLeg(
                        UUID.randomUUID(), transferId, ownerId, destination.getId(), new BigDecimal("20.00"),
                        TransactionType.TRANSFER_IN, date, "Transfer", null, null
                )
        );
    }
}
