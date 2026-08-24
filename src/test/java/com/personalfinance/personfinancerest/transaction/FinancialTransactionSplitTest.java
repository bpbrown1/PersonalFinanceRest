package com.personalfinance.personfinancerest.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransactionSplitTest {

    @Test
    void replacesRowsInRequestOrderWhileRetainingExistingIds() {
        FinancialTransaction transaction = transaction();
        UUID groceriesId = UUID.randomUUID();
        UUID diningId = UUID.randomUUID();
        UUID travelId = UUID.randomUUID();
        transaction.replaceSplits(List.of(
                replacement(null, groceriesId, "60.00"),
                replacement(null, diningId, "40.00")
        ));
        UUID retainedId = transaction.getSplits().get(1).getId();

        transaction.replaceSplits(List.of(
                replacement(retainedId, diningId, "25.00"),
                replacement(null, travelId, "75.00")
        ));

        assertThat(transaction.getSplits()).hasSize(2);
        assertThat(transaction.getSplits().get(0).getId()).isEqualTo(retainedId);
        assertThat(transaction.getSplits().get(0).getPosition()).isZero();
        assertThat(transaction.getSplits().get(0).getAmount()).isEqualByComparingTo("25.00");
        assertThat(transaction.getSplits().get(1).getCategoryId()).isEqualTo(travelId);
        assertThat(transaction.getSplits().get(1).getPosition()).isEqualTo(1);
        assertThat(transaction.getSplits()).noneMatch(split -> split.getCategoryId().equals(groceriesId));
    }

    @Test
    void responseExposesTheCompleteOrderedAllocation() {
        FinancialTransaction transaction = transaction();
        UUID firstCategoryId = UUID.randomUUID();
        UUID secondCategoryId = UUID.randomUUID();
        transaction.replaceSplits(List.of(
                replacement(null, firstCategoryId, "10.00"),
                replacement(null, secondCategoryId, "90.00")
        ));

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.splits()).extracting(TransactionSplitResponse::position)
                .containsExactly(0, 1);
        assertThat(response.splits()).extracting(TransactionSplitResponse::categoryId)
                .containsExactly(firstCategoryId, secondCategoryId);
    }

    private FinancialTransaction transaction() {
        return new FinancialTransaction(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                new BigDecimal("100.00"), TransactionType.EXPENSE, LocalDate.now(),
                "Split purchase", null, null, null
        );
    }

    private FinancialTransaction.SplitReplacement replacement(UUID id, UUID categoryId, String amount) {
        return new FinancialTransaction.SplitReplacement(id, categoryId, new BigDecimal(amount));
    }
}
