package com.personalfinance.personfinancerest.account.activity;

import java.util.UUID;

@FunctionalInterface
public interface FinancialAccountActivitySource {

    boolean existsFor(UUID accountId);
}
