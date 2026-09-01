package com.personalfinance.personfinancerest.recurringexpense;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
class RecurringExpenseOccurrenceProjector {

    List<LocalDate> dueDates(RecurringExpense expense, LocalDate from, LocalDate to) {
        LocalDate effectiveEnd = effectiveEnd(expense, to);
        if (effectiveEnd.isBefore(from) || expense.getAnchorDate().isAfter(effectiveEnd)) {
            return List.of();
        }

        long interval = expense.getIntervalMonths();
        long monthsToFrom = Math.max(0,
                ChronoUnit.MONTHS.between(YearMonth.from(expense.getAnchorDate()), YearMonth.from(from)));
        long occurrenceIndex = monthsToFrom / interval;
        LocalDate dueDate = dueDate(expense, occurrenceIndex);
        while (dueDate.isBefore(from)) {
            dueDate = dueDate(expense, ++occurrenceIndex);
        }

        List<LocalDate> dates = new ArrayList<>();
        while (!dueDate.isAfter(effectiveEnd)) {
            dates.add(dueDate);
            dueDate = dueDate(expense, ++occurrenceIndex);
        }
        return List.copyOf(dates);
    }

    private LocalDate effectiveEnd(RecurringExpense expense, LocalDate requestedEnd) {
        LocalDate result = requestedEnd;
        if (expense.getEndDate() != null && expense.getEndDate().isBefore(result)) {
            result = expense.getEndDate();
        }
        if (expense.getArchivedAt() != null) {
            LocalDate archiveDate = expense.getArchivedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (archiveDate.isBefore(result)) {
                result = archiveDate;
            }
        }
        return result;
    }

    private LocalDate dueDate(RecurringExpense expense, long occurrenceIndex) {
        YearMonth month = YearMonth.from(expense.getAnchorDate())
                .plusMonths(Math.multiplyExact(occurrenceIndex, expense.getIntervalMonths()));
        return month.atDay(Math.min(expense.getAnchorDate().getDayOfMonth(), month.lengthOfMonth()));
    }
}
