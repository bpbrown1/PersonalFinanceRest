package com.personalfinance.personfinancerest.budget;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderBudgetLinesRequest(
        @NotNull List<@NotNull UUID> lineIds
) {
}
