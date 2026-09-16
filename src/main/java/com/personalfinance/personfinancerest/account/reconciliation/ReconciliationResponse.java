package com.personalfinance.personfinancerest.account.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationResponse(
        UUID id,
        UUID accountId,
        LocalDate statementDate,
        BigDecimal statementBalance,
        BigDecimal calculatedBalance,
        BigDecimal difference,
        String ledgerToken,
        String explanation,
        UUID transactionId,
        Instant createdAt
) {
    static ReconciliationResponse from(AccountReconciliation reconciliation) {
        return new ReconciliationResponse(
                reconciliation.getId(), reconciliation.getAccountId(), reconciliation.getStatementDate(),
                reconciliation.getStatementBalance(), reconciliation.getCalculatedBalance(),
                reconciliation.getDifference(), reconciliation.getLedgerToken(),
                reconciliation.getExplanation(), reconciliation.getTransactionId(),
                reconciliation.getCreatedAt()
        );
    }
}
