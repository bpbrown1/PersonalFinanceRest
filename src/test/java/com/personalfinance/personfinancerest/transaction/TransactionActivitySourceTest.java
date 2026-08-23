package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TransactionActivitySourceTest {

    @Test
    void retainedSoftDeletedTransactionsStillCountAsAccountActivity() {
        FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
        UUID accountId = UUID.randomUUID();
        given(repository.existsByAccountId(accountId)).willReturn(true);

        assertThat(new TransactionActivitySource(repository).existsFor(accountId)).isTrue();
    }
}
