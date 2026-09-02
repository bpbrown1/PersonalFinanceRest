package com.personalfinance.personfinancerest.account.management;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialAccountResponse(
        UUID id,
        UUID ownerId,
        String name,
        AccountType type,
        AccountClassification classification,
        String currency,
        LocalDate openingDate,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        BigDecimal interestRate,
        InterestRateType interestRateType,
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
                account.getType().classification(),
                account.getCurrency(),
                account.getOpeningDate(),
                account.getOpeningBalance(),
                account.getCurrentBalance(),
                account.getInterestRate(),
                account.getInterestRateType(),
                account.getStatus(),
                account.getArchivedAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
