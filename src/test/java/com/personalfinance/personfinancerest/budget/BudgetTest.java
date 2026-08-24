package com.personalfinance.personfinancerest.budget;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetTest {

    @Test
    void totalsOnlyActiveLinesAtFixedDecimalScale() {
        Budget budget = budget();
        BudgetLine first = budget.addLine(UUID.randomUUID(), new BigDecimal("50.00"));
        budget.addLine(UUID.randomUUID(), new BigDecimal("25.50"));

        assertThat(budget.getTotalPlanned()).isEqualByComparingTo("75.50");
        first.archive(Instant.now());
        assertThat(budget.getTotalPlanned()).isEqualByComparingTo("25.50");
        first.restore();
        assertThat(budget.getTotalPlanned()).isEqualByComparingTo("75.50");
    }

    @Test
    void preservesStableLineIdsWhileReordering() {
        Budget budget = budget();
        BudgetLine first = budget.addLine(UUID.randomUUID(), new BigDecimal("10.00"));
        BudgetLine second = budget.addLine(UUID.randomUUID(), new BigDecimal("20.00"));

        budget.reorder(List.of(second.getId(), first.getId()));

        assertThat(budget.getLines()).extracting(BudgetLine::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(budget.getLines()).extracting(BudgetLine::getPosition).containsExactly(0, 1);
    }

    @Test
    void normalizesMetadataAndUsesRecoverableLifecycle() {
        Budget budget = new Budget(
                UUID.randomUUID(), UUID.randomUUID(), " August Plan ", "usd",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );

        assertThat(budget.getName()).isEqualTo("August Plan");
        assertThat(budget.getCurrency()).isEqualTo("USD");
        assertThat(budget.getPeriodType()).isEqualTo(BudgetPeriodType.MONTHLY);
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.ACTIVE);

        budget.archive(Instant.now());
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.ARCHIVED);
        budget.restore();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.ACTIVE);
    }

    private Budget budget() {
        return new Budget(
                UUID.randomUUID(), UUID.randomUUID(), "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
    }
}
