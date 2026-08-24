package com.personalfinance.personfinancerest.transaction;

import org.springframework.data.domain.Page;

import java.util.List;

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
        return new TransactionPageResponse(
                result.getContent().stream().map(TransactionResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                criteria.sortBy(), criteria.direction().name().toLowerCase()
        );
    }
}
