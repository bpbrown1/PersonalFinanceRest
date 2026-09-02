package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseOccurrenceReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public record TransactionResponse(
        UUID id,
        UUID ownerId,
        UUID accountId,
        UUID categoryId,
        UUID transferId,
        List<TransactionSplitResponse> splits,
        BigDecimal amount,
        BigDecimal balanceImpact,
        TransactionType type,
        LocalDate transactionDate,
        String description,
        String merchantPayee,
        String notes,
        String externalReference,
        RecurringExpenseOccurrenceReference recurringExpenseOccurrence,
        TransactionStatus status,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    static TransactionResponse from(FinancialTransaction transaction) {
        return from(transaction, null);
    }

    static TransactionResponse from(FinancialTransaction transaction,
                                    RecurringExpenseOccurrenceReference occurrence) {
        return new TransactionResponse(
                transaction.getId(), transaction.getOwnerId(), transaction.getAccountId(),
                transaction.getCategoryId(), transaction.getTransferId(),
                transaction.getSplits().stream().map(TransactionSplitResponse::from).toList(),
                transaction.getAmount(), transaction.balanceImpact(),
                transaction.getType(), transaction.getTransactionDate(), transaction.getDescription(),
                transaction.getMerchantPayee(), transaction.getNotes(), transaction.getExternalReference(),
                occurrence,
                transaction.getStatus(), transaction.getDeletedAt(), transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
