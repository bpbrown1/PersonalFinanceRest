package com.personalfinance.personfinancerest.transaction;

import java.math.BigDecimal;

interface TransactionSummaryAggregate {

    String getCurrency();

    BigDecimal getIncome();

    BigDecimal getSpending();

    long getTransactionCount();
}
