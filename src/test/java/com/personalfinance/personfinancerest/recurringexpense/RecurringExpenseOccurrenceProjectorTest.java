package com.personalfinance.personfinancerest.recurringexpense;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringExpenseOccurrenceProjectorTest {

    private final RecurringExpenseOccurrenceProjector projector = new RecurringExpenseOccurrenceProjector();

    @Test
    void clampsEachOccurrenceFromTheAnchorWithoutMonthEndDrift() {
        RecurringExpense expense = expense(LocalDate.of(2027, 1, 31), null, 1);

        assertThat(projector.dueDates(
                expense, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 4, 30)
        )).containsExactly(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30)
        );
    }

    @Test
    void handlesLeapYearsIntervalsRangesAndEndDates() {
        RecurringExpense expense = expense(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 2, 28), 12);

        assertThat(projector.dueDates(
                expense, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31)
        )).containsExactly(LocalDate.of(2025, 2, 28), LocalDate.of(2026, 2, 28));
    }

    @Test
    void archivedDefinitionsRetainOnlyOccurrencesBeforeTheArchiveDate() {
        RecurringExpense expense = expense(LocalDate.of(2026, 1, 15), null, 1);
        expense.archive(Instant.parse("2026-03-10T12:00:00Z"));

        assertThat(projector.dueDates(
                expense, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31)
        )).containsExactly(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15));
    }

    private RecurringExpense expense(LocalDate anchorDate, LocalDate endDate, int intervalMonths) {
        return new RecurringExpense(
                UUID.randomUUID(), UUID.randomUUID(), "Bill", new BigDecimal("10.00"), "USD",
                UUID.randomUUID(), null, anchorDate, endDate, intervalMonths
        );
    }
}
