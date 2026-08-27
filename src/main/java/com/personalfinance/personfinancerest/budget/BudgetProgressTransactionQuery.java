package com.personalfinance.personfinancerest.budget;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

record BudgetProgressTransactionQuery(
        Scope scope,
        UUID lineId,
        UUID accountId,
        UUID categoryId,
        boolean uncategorized,
        int page,
        int size,
        String sort,
        String direction
) {

    static BudgetProgressTransactionQuery from(
            String scope,
            UUID lineId,
            UUID accountId,
            UUID categoryId,
            boolean uncategorized,
            int page,
            int size,
            String sort,
            String direction
    ) {
        Scope parsedScope = Scope.from(scope);
        Map<String, String> errors = new LinkedHashMap<>();
        switch (parsedScope) {
            case OVERALL -> {
                reject(lineId != null, "lineId", "is only supported for line scope", errors);
                reject(uncategorized, "uncategorized", "is only supported for unbudgeted scope", errors);
            }
            case LINE -> {
                reject(lineId == null, "lineId", "is required for line scope", errors);
                reject(categoryId != null, "categoryId", "is not supported for line scope", errors);
                reject(uncategorized, "uncategorized", "is not supported for line scope", errors);
            }
            case UNBUDGETED -> {
                reject(lineId != null, "lineId", "is not supported for unbudgeted scope", errors);
                reject(categoryId == null && !uncategorized, "categoryId",
                        "or uncategorized=true is required for unbudgeted scope", errors);
                reject(categoryId != null && uncategorized, "uncategorized",
                        "cannot be combined with categoryId", errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidBudgetRequestException(errors);
        }
        return new BudgetProgressTransactionQuery(
                parsedScope, lineId, accountId, categoryId, uncategorized,
                page, size, sort, direction
        );
    }

    private static void reject(boolean invalid, String field, String message, Map<String, String> errors) {
        if (invalid) {
            errors.put(field, message);
        }
    }

    enum Scope {
        OVERALL,
        LINE,
        UNBUDGETED;

        private static Scope from(String value) {
            try {
                return Scope.valueOf((value == null ? "overall" : value.trim()).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidBudgetRequestException(Map.of(
                        "scope", "must be one of: overall, line, unbudgeted"
                ));
            }
        }

        String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
