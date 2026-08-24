package com.personalfinance.personfinancerest.transaction;

import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

record TransactionSearchCriteria(
        TransactionStatusFilter status,
        UUID accountId,
        LocalDate from,
        LocalDate to,
        UUID categoryId,
        TransactionType type,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String text,
        int page,
        int size,
        String sortBy,
        Sort.Direction direction
) {
    static final int MAX_PAGE_SIZE = 100;

    static TransactionSearchCriteria from(
            String status, UUID accountId, LocalDate from, LocalDate to, UUID categoryId, String type,
            BigDecimal minAmount, BigDecimal maxAmount, String text, int page, int size,
            String sortBy, String direction
    ) {
        validateRanges(from, to, minAmount, maxAmount);
        if (page < 0) {
            throw new InvalidTransactionSearchException("page", "must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidTransactionSearchException(
                    "size", "must be between 1 and " + MAX_PAGE_SIZE
            );
        }
        String normalizedSort = sortBy == null ? "date" : sortBy.trim().toLowerCase(Locale.ROOT);
        if (!normalizedSort.equals("date") && !normalizedSort.equals("amount")) {
            throw new InvalidTransactionSearchException("sort", "must be one of: date, amount");
        }
        Sort.Direction parsedDirection;
        try {
            parsedDirection = Sort.Direction.fromString(direction == null ? "desc" : direction.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransactionSearchException("sort", "direction must be one of: asc, desc");
        }
        return new TransactionSearchCriteria(
                TransactionStatusFilter.fromValue(status), accountId, from, to, categoryId,
                parseType(type), minAmount, maxAmount, normalizeText(text), page, size,
                normalizedSort, parsedDirection
        );
    }

    static TransactionType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return TransactionType.fromValue(type);
        } catch (RuntimeException exception) {
            throw new InvalidTransactionSearchException(
                    "type", "must be one of: income, expense, transfer_out, transfer_in"
            );
        }
    }

    static void validateRanges(LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransactionDateRangeException();
        }
        if ((minAmount != null && minAmount.signum() < 0)
                || (maxAmount != null && maxAmount.signum() < 0)
                || (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0)) {
            throw new InvalidTransactionSearchException(
                    "amountRange", "minAmount must be non-negative and on or below maxAmount"
            );
        }
    }

    private static String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    Sort pageableSort() {
        Sort primary = sortBy.equals("amount")
                ? Sort.by(new Sort.Order(direction, "amount"))
                : Sort.by(new Sort.Order(direction, "transactionDate"));
        if (sortBy.equals("date")) {
            primary = primary.and(Sort.by(new Sort.Order(direction, "createdAt")));
        } else {
            primary = primary.and(Sort.by(Sort.Order.desc("transactionDate")));
        }
        return primary.and(Sort.by(Sort.Order.asc("id")));
    }
}
