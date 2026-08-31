package com.personalfinance.personfinancerest.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BudgetProgressDrillDown(
        LocalDate from,
        LocalDate to,
        UUID accountId,
        List<UUID> categoryIds,
        String type,
        String status,
        List<UUID> transactionIds,
        String transactionsPath
) {
}
