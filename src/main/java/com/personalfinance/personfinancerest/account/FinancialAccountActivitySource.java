package com.personalfinance.personfinancerest.account;

import java.util.UUID;

@FunctionalInterface
interface FinancialAccountActivitySource {

    boolean existsFor(UUID accountId);
}
