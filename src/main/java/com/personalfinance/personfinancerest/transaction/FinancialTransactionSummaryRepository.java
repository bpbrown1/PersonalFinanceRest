package com.personalfinance.personfinancerest.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface FinancialTransactionSummaryRepository {

    List<TransactionSummaryAggregate> summarize(
            UUID ownerId,
            LocalDate from,
            LocalDate to,
            TransactionType incomeType,
            TransactionType expenseType,
            UUID accountId,
            UUID categoryId,
            TransactionType transactionType
    );
}
