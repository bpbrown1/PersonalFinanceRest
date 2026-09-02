package com.personalfinance.personfinancerest.recurringexpense;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MatchRecurringExpenseRequest(@NotNull UUID transactionId) {
}
