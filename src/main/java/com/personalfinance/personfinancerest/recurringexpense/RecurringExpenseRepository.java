package com.personalfinance.personfinancerest.recurringexpense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    Optional<RecurringExpense> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<RecurringExpense> findAllByOwnerIdAndArchivedAtIsNullOrderByNameAscIdAsc(UUID ownerId);

    List<RecurringExpense> findAllByOwnerIdAndArchivedAtIsNotNullOrderByNameAscIdAsc(UUID ownerId);

    List<RecurringExpense> findAllByOwnerIdOrderByNameAscIdAsc(UUID ownerId);
}
