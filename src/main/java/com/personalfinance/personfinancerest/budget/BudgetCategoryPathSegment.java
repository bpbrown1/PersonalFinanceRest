package com.personalfinance.personfinancerest.budget;

import java.util.UUID;

public record BudgetCategoryPathSegment(
        UUID categoryId,
        String name
) {
}
