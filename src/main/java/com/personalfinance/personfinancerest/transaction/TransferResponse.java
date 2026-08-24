package com.personalfinance.personfinancerest.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID ownerId,
        UUID sourceTransactionId,
        UUID destinationTransactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal sourceAmount,
        BigDecimal destinationAmount,
        LocalDate transactionDate,
        String description,
        String notes,
        String externalReference,
        TransactionStatus status,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    static TransferResponse from(List<FinancialTransaction> legs) {
        if (legs.size() != 2) {
            throw new TransactionConflictException("A transfer must contain exactly two linked legs");
        }
        FinancialTransaction source = leg(legs, TransactionType.TRANSFER_OUT);
        FinancialTransaction destination = leg(legs, TransactionType.TRANSFER_IN);
        if (!source.getOwnerId().equals(destination.getOwnerId())
                || !source.getTransferId().equals(destination.getTransferId())
                || source.getStatus() != destination.getStatus()
                || source.getAccountId().equals(destination.getAccountId())
                || !source.getTransactionDate().equals(destination.getTransactionDate())
                || !source.getDescription().equals(destination.getDescription())
                || !Objects.equals(source.getNotes(), destination.getNotes())
                || !Objects.equals(source.getExternalReference(), destination.getExternalReference())) {
            throw new TransactionConflictException("The linked transfer legs are inconsistent");
        }
        return new TransferResponse(
                source.getTransferId(), source.getOwnerId(), source.getId(), destination.getId(),
                source.getAccountId(), destination.getAccountId(), source.getAmount(), destination.getAmount(),
                source.getTransactionDate(), source.getDescription(), source.getNotes(),
                source.getExternalReference(), source.getStatus(), source.getDeletedAt(),
                source.getCreatedAt(), latest(source.getUpdatedAt(), destination.getUpdatedAt())
        );
    }

    private static FinancialTransaction leg(List<FinancialTransaction> legs, TransactionType type) {
        return legs.stream().filter(leg -> leg.getType() == type).findFirst()
                .orElseThrow(() -> new TransactionConflictException("A linked transfer leg is missing: " + type));
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }
}
