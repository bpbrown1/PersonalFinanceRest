package com.personalfinance.personfinancerest.recurringexpense;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface RecurringExpenseTransactionSource {

    RecurringExpenseTransactionCandidate findOwned(UUID ownerId, UUID transactionId);

    Map<UUID, RecurringExpenseTransactionCandidate> findOwned(UUID ownerId, Collection<UUID> transactionIds);
}
