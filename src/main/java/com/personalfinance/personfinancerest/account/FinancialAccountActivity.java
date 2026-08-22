package com.personalfinance.personfinancerest.account;

import java.util.UUID;

@FunctionalInterface
interface FinancialAccountActivity {

    boolean existsFor(UUID accountId);
}
