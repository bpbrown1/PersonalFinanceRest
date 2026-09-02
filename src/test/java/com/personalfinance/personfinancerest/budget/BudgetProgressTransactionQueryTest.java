package com.personalfinance.personfinancerest.budget;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetProgressTransactionQueryTest {

    @Test
    void acceptsEachSupportedBookmarkableScope() {
        UUID lineId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        assertThat(query("overall", null, categoryId, false).scope())
                .isEqualTo(BudgetProgressTransactionQuery.Scope.OVERALL);
        assertThat(query("line", lineId, null, false).lineId()).isEqualTo(lineId);
        assertThat(BudgetProgressTransactionQuery.from(
                "component", null, "expense:2026-08-31", null, null,
                false, 0, 25, "date", "desc"
        ).scope()).isEqualTo(BudgetProgressTransactionQuery.Scope.COMPONENT);
        assertThat(query("unbudgeted", null, categoryId, false).categoryId()).isEqualTo(categoryId);
        assertThat(query("unbudgeted", null, null, true).uncategorized()).isTrue();
    }

    @Test
    void defaultsToOverallScope() {
        assertThat(query(null, null, null, false).scope())
                .isEqualTo(BudgetProgressTransactionQuery.Scope.OVERALL);
    }

    @Test
    void rejectsUnknownAndIncompatibleScopeParameters() {
        assertThatThrownBy(() -> query("unknown", null, null, false))
                .isInstanceOf(InvalidBudgetRequestException.class)
                .satisfies(exception -> assertThat(((InvalidBudgetRequestException) exception).getFieldErrors())
                        .containsKey("scope"));
        assertThatThrownBy(() -> query("line", null, null, false))
                .isInstanceOf(InvalidBudgetRequestException.class)
                .satisfies(exception -> assertThat(((InvalidBudgetRequestException) exception).getFieldErrors())
                        .containsEntry("lineId", "is required for line scope"));
        assertThatThrownBy(() -> query("unbudgeted", null, null, false))
                .isInstanceOf(InvalidBudgetRequestException.class);
        assertThatThrownBy(() -> query("unbudgeted", null, UUID.randomUUID(), true))
                .isInstanceOf(InvalidBudgetRequestException.class)
                .satisfies(exception -> assertThat(((InvalidBudgetRequestException) exception).getFieldErrors())
                        .containsKey("uncategorized"));
        assertThatThrownBy(() -> BudgetProgressTransactionQuery.from(
                "component", null, " ", null, null,
                false, 0, 25, "date", "desc"
        )).isInstanceOf(InvalidBudgetRequestException.class)
                .satisfies(exception -> assertThat(
                        ((InvalidBudgetRequestException) exception).getFieldErrors())
                        .containsKey("occurrenceKey"));
    }

    private BudgetProgressTransactionQuery query(
            String scope, UUID lineId, UUID categoryId, boolean uncategorized
    ) {
        return BudgetProgressTransactionQuery.from(
                scope, lineId, null, null, categoryId, uncategorized,
                0, 25, "date", "desc"
        );
    }
}
