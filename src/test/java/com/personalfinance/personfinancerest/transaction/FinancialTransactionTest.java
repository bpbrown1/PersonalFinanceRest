package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransactionTest {

    @Test
    void normalizesOptionalTextAndManagesRecoverableDeletionIdempotently() {
        FinancialTransaction transaction = transaction(TransactionType.EXPENSE, "25.00");

        assertThat(transaction.getDescription()).isEqualTo("Groceries");
        assertThat(transaction.getMerchantPayee()).isEqualTo("Market");
        assertThat(transaction.getNotes()).isNull();
        assertThat(transaction.getExternalReference()).isNull();
        assertThat(transaction.balanceImpact()).isEqualByComparingTo("-25.00");

        Instant deletedAt = Instant.parse("2026-08-23T12:00:00.123456Z");
        transaction.softDelete(deletedAt);
        transaction.softDelete(deletedAt.plusSeconds(1));
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.DELETED);
        assertThat(transaction.getDeletedAt()).isEqualTo(deletedAt);

        transaction.restore();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.ACTIVE);
    }

    @Test
    void replacesAllEditableFieldsAndCanClearOptionalValues() {
        FinancialTransaction transaction = transaction(TransactionType.EXPENSE, "25.00");
        UUID accountId = UUID.randomUUID();

        transaction.replace(
                accountId, null, new BigDecimal("100.00"), TransactionType.INCOME,
                LocalDate.of(2026, 8, 22), " Salary ", null, " ", null
        );

        assertThat(transaction.getAccountId()).isEqualTo(accountId);
        assertThat(transaction.getCategoryId()).isNull();
        assertThat(transaction.getDescription()).isEqualTo("Salary");
        assertThat(transaction.getNotes()).isNull();
        assertThat(transaction.balanceImpact()).isEqualByComparingTo("100.00");
    }

    private FinancialTransaction transaction(TransactionType type, String amount) {
        return new FinancialTransaction(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(amount), type, LocalDate.of(2026, 8, 22),
                " Groceries ", " Market ", " ", null
        );
    }
}
