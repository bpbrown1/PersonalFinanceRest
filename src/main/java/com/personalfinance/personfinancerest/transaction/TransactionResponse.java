package com.personalfinance.personfinancerest.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID ownerId,
        UUID accountId,
        UUID categoryId,
        BigDecimal amount,
        BigDecimal balanceImpact,
        TransactionType type,
        LocalDate transactionDate,
        String description,
        String merchantPayee,
        String notes,
        String externalReference,
        TransactionStatus status,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    static TransactionResponse from(FinancialTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getOwnerId(), transaction.getAccountId(),
                transaction.getCategoryId(), transaction.getAmount(), transaction.balanceImpact(),
                transaction.getType(), transaction.getTransactionDate(), transaction.getDescription(),
                transaction.getMerchantPayee(), transaction.getNotes(), transaction.getExternalReference(),
                transaction.getStatus(), transaction.getDeletedAt(), transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
