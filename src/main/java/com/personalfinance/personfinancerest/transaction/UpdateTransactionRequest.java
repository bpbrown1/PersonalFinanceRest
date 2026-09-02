package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.shared.validation.NonZero;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseOccurrenceSelection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public record UpdateTransactionRequest(
        @NotNull UUID accountId,
        @NotNull @NonZero @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank @Size(max = 255) String description,
        @NotNull TransactionType type,
        UUID categoryId,
        List<@Valid TransactionSplitRequest> splits,
        @Size(max = 255) String merchantPayee,
        @Size(max = 2000) String notes,
        @Size(max = 255) String externalReference,
        @Valid RecurringExpenseOccurrenceSelection recurringExpenseOccurrence
) {
    public UpdateTransactionRequest(
            UUID accountId, BigDecimal amount, LocalDate transactionDate, String description,
            TransactionType type, UUID categoryId, List<TransactionSplitRequest> splits,
            String merchantPayee, String notes, String externalReference) {
        this(accountId, amount, transactionDate, description, type, categoryId, splits,
                merchantPayee, notes, externalReference, null);
    }
}
