package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionSearchCriteriaTest {

    @Test
    void appliesDefaultsAndNormalizesSearchValues() {
        TransactionSearchCriteria criteria = criteria(
                "active", null, null, null, " income ", null, null, "  Market  ",
                0, 25, " DATE ", " DESC "
        );

        assertThat(criteria.status()).isEqualTo(TransactionStatusFilter.ACTIVE);
        assertThat(criteria.type()).isEqualTo(TransactionType.INCOME);
        assertThat(criteria.text()).isEqualTo("Market");
        assertThat(criteria.sortBy()).isEqualTo("date");
        assertThat(criteria.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(criteria.pageableSort().getOrderFor("transactionDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(criteria.pageableSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void amountSortUsesStableDateAndIdentifierTieBreakers() {
        TransactionSearchCriteria criteria = criteria(
                "all", null, null, null, "transfer_in", new BigDecimal("10.00"),
                new BigDecimal("20.00"), null, 2, 10, "amount", "asc"
        );

        assertThat(criteria.pageableSort().getOrderFor("amount").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(criteria.pageableSort().getOrderFor("transactionDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(criteria.pageableSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rejectsInvalidRangesPagingSortAndType() {
        assertInvalid(
                () -> criteria("active", LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1),
                        null, null, null, null, null, 0, 25, "date", "desc"),
                InvalidTransactionDateRangeException.class
        );
        assertInvalid(
                () -> criteria("active", null, null, null, null, new BigDecimal("20.00"),
                        new BigDecimal("10.00"), null, 0, 25, "date", "desc"),
                InvalidTransactionSearchException.class
        );
        assertInvalid(() -> criteria("active", null, null, null, null, null, null,
                null, -1, 25, "date", "desc"), InvalidTransactionSearchException.class);
        assertInvalid(() -> criteria("active", null, null, null, null, null, null,
                null, 0, 101, "date", "desc"), InvalidTransactionSearchException.class);
        assertInvalid(() -> criteria("active", null, null, null, null, null, null,
                null, 0, 25, "description", "desc"), InvalidTransactionSearchException.class);
        assertInvalid(() -> criteria("active", null, null, null, "unknown", null, null,
                null, 0, 25, "date", "desc"), InvalidTransactionSearchException.class);
    }

    private TransactionSearchCriteria criteria(
            String status, LocalDate from, LocalDate to, java.util.UUID categoryId, String type,
            BigDecimal minAmount, BigDecimal maxAmount, String text, int page, int size,
            String sort, String direction
    ) {
        return TransactionSearchCriteria.from(
                status, null, from, to, categoryId, type, minAmount, maxAmount,
                text, page, size, sort, direction
        );
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                               Class<? extends Throwable> type) {
        assertThatThrownBy(callable).isInstanceOf(type);
    }
}
