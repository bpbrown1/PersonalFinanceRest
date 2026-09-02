package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransactionSummaryRepositoryTest {

    @Test
    void omitsEveryOptionalPredicateAndUntypedNullCheckWhenFiltersAreAbsent() {
        String query = FinancialTransactionSummaryRepositoryImpl.queryText(null, null, null, null, null);

        assertThat(query)
                .doesNotContain(":accountId is null", ":categoryId is null", ":transactionType is null",
                        ":fromDate is null", ":toDate is null")
                .doesNotContain(":accountId")
                .doesNotContain(":categoryId")
                .doesNotContain(":transactionType")
                .doesNotContain(":fromDate")
                .doesNotContain(":toDate");
    }

    @Test
    void addsOnlySuppliedOptionalPredicates() {
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        String query = FinancialTransactionSummaryRepositoryImpl.queryText(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                accountId, categoryId, TransactionType.EXPENSE
        );

        assertThat(query)
                .doesNotContain(":accountId is null", ":categoryId is null", ":transactionType is null",
                        ":fromDate is null", ":toDate is null")
                .contains("entry.accountId = :accountId")
                .contains("entry.categoryId = :categoryId")
                .contains("split.categoryId = :categoryId")
                .contains("entry.type = :transactionType")
                .contains("entry.transactionDate >= :fromDate")
                .contains("entry.transactionDate <= :toDate");
    }
}
