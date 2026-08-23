package com.personalfinance.personfinancerest.account.management;

/**
 * Records the opening balance history associated with an account lifecycle change.
 */
public interface OpeningBalanceHistory {

    void createFor(FinancialAccount account);

    void updateFor(FinancialAccount account);
}
