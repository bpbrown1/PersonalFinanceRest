package com.personalfinance.personfinancerest.account.activity;

import java.util.UUID;

@FunctionalInterface
public interface FinancialAccountActivity {

    boolean existsFor(UUID accountId);
}
