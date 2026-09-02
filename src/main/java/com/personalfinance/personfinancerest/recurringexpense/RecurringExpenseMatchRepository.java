package com.personalfinance.personfinancerest.recurringexpense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RecurringExpenseMatchRepository extends JpaRepository<RecurringExpenseMatch, UUID> {

    Optional<RecurringExpenseMatch> findByOwnerIdAndRecurringExpenseIdAndDueDate(
            UUID ownerId, UUID recurringExpenseId, LocalDate dueDate);

    Optional<RecurringExpenseMatch> findByOwnerIdAndTransactionId(UUID ownerId, UUID transactionId);

    List<RecurringExpenseMatch> findAllByOwnerIdAndTransactionIdIn(UUID ownerId, Collection<UUID> transactionIds);

    List<RecurringExpenseMatch> findAllByOwnerIdAndRecurringExpenseId(UUID ownerId, UUID recurringExpenseId);

    List<RecurringExpenseMatch> findAllByOwnerIdAndRecurringExpenseIdInAndDueDateBetween(
            UUID ownerId, Collection<UUID> recurringExpenseIds, LocalDate from, LocalDate to);
}
