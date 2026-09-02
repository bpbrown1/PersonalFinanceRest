package com.personalfinance.personfinancerest.transaction;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseOccurrenceReference;

public record TransactionPageResponse(
        List<TransactionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sortBy,
        String sortDirection
) {
    static TransactionPageResponse from(Page<FinancialTransaction> result, TransactionSearchCriteria criteria) {
        return from(result, criteria, Map.of());
    }

    static TransactionPageResponse from(
            Page<FinancialTransaction> result,
            TransactionSearchCriteria criteria,
            Map<UUID, RecurringExpenseOccurrenceReference> occurrences) {
        return new TransactionPageResponse(
                result.getContent().stream()
                        .map(transaction -> TransactionResponse.from(
                                transaction, occurrences.get(transaction.getId())))
                        .toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                criteria.sortBy(), criteria.direction().name().toLowerCase()
        );
    }
}
