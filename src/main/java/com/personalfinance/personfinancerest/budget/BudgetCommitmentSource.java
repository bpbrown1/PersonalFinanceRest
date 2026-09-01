package com.personalfinance.personfinancerest.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BudgetCommitmentSource {
    List<BudgetScheduledCommitment> findScheduledCommitments(
            UUID ownerId, String currency, LocalDate from, LocalDate to, UUID accountId
    );
}
