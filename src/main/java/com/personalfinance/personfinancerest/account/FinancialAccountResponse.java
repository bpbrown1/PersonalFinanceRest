package com.personalfinance.personfinancerest.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialAccountResponse(
        UUID id,
        UUID ownerId,
        String name,
        AccountType type,
        String currency,
        LocalDate openingDate,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        AccountStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
    static FinancialAccountResponse from(FinancialAccount account) {
        return new FinancialAccountResponse(
                account.getId(),
                account.getOwnerId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getOpeningDate(),
                account.getOpeningBalance(),
                account.getCurrentBalance(),
                account.getStatus(),
                account.getArchivedAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
