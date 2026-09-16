package com.personalfinance.personfinancerest.account.reconciliation;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReconciliationPageResponse(
        List<ReconciliationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static ReconciliationPageResponse from(Page<AccountReconciliation> result) {
        return new ReconciliationPageResponse(
                result.getContent().stream().map(ReconciliationResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }
}
