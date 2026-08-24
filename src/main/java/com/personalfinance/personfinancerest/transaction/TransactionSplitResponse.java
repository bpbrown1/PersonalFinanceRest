package com.personalfinance.personfinancerest.transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionSplitResponse(
        UUID id,
        int position,
        UUID categoryId,
        BigDecimal amount
) {
    static TransactionSplitResponse from(TransactionSplit split) {
        return new TransactionSplitResponse(
                split.getId(), split.getPosition(), split.getCategoryId(), split.getAmount()
        );
    }
}
