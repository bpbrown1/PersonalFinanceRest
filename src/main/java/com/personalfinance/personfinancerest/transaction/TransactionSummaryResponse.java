package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.shared.money.MoneyValues;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
        String currency,
        BigDecimal income,
        BigDecimal spending,
        BigDecimal netImpact,
        long transactionCount
) {

    static TransactionSummaryResponse from(TransactionSummaryAggregate aggregate) {
        BigDecimal income = MoneyValues.amountOrZero(aggregate.getIncome());
        BigDecimal spending = MoneyValues.amountOrZero(aggregate.getSpending());
        return new TransactionSummaryResponse(
                aggregate.getCurrency(), income, spending, income.subtract(spending),
                aggregate.getTransactionCount()
        );
    }
}
