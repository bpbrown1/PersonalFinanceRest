package com.personalfinance.personfinancerest.recurringexpense;

import com.personalfinance.personfinancerest.budget.BudgetCommitmentSource;
import com.personalfinance.personfinancerest.budget.BudgetScheduledCommitment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
class RecurringExpenseBudgetCommitmentSource implements BudgetCommitmentSource {

    private final RecurringExpenseService service;

    RecurringExpenseBudgetCommitmentSource(RecurringExpenseService service) {
        this.service = service;
    }

    @Override
    public List<BudgetScheduledCommitment> findScheduledCommitments(
            UUID ownerId, String currency, LocalDate from, LocalDate to, UUID accountId) {
        return service.project(ownerId, currency, from, to, null).stream()
                .map(projected -> {
                    RecurringExpenseOccurrenceResponse occurrence = projected.response();
                    RecurringExpenseLinkedTransactionResponse linked = occurrence.linkedTransaction();
                    return new BudgetScheduledCommitment(
                            occurrence.occurrenceKey(), occurrence.recurringExpenseId(), occurrence.name(),
                            occurrence.dueDate(), occurrence.amount(), occurrence.currency(),
                            occurrence.categoryId(), occurrence.accountId(),
                            occurrence.status() == RecurringExpenseOccurrenceStatus.SATISFIED,
                            occurrence.actualAmount(), occurrence.variance(),
                            linked == null ? null : linked.id(),
                            linked == null ? null : linked.accountId(),
                            linked == null ? null : linked.transactionDate()
                    );
                })
                .filter(commitment -> accountId == null || accountId.equals(
                        commitment.linkedTransactionAccountId() == null
                                ? commitment.accountId() : commitment.linkedTransactionAccountId()))
                .toList();
    }
}
