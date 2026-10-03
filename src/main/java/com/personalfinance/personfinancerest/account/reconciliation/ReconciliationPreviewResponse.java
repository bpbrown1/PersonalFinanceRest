package com.personalfinance.personfinancerest.account.reconciliation;

import com.personalfinance.personfinancerest.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationPreviewResponse(
        UUID accountId,
        String currency,
        LocalDate statementDate,
        BigDecimal statementBalance,
        BigDecimal calculatedBalance,
        BigDecimal difference,
        TransactionType adjustmentType,
        int transactionCount,
        String ledgerToken
) {
}
